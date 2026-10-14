"""Game directory isolation and the two launch backends.

The Prism half follows the measured Prism CLI behaviour recorded in the
``prism-pack-lifecycle`` skill:

* ``-I`` must receive a **local** file; handing Prism a url makes it open dialogs
  and hang or abort with no one to click them.
* ``-d <root>`` isolates the whole data root, which is why jt can keep its
  Prism instances under ``<JT_HOME>`` and never touch the user's own launcher.
* a new instance id is found by diffing the instances directory before and after
  the import (``name=`` in ``instance.cfg`` is only a fallback).
* ``launch`` is zero-interaction and reuses the logged-in account of that root.
* ``latest.log`` may belong to a previous session, so a session is only accepted
  when the file is newer than the run's start timestamp.

The gradle half launches through the checkout's own run task
(``./gradlew <task> --no-daemon -PjechRunDir=<gameDir>``): the game directory is
handed to the build, the client boots inside it, and ``latest.log`` is read from
``<gameDir>/logs``. Gradle passes the client's own arguments through an
``@argfile``, so neither argv nor the environment is visible in ``ps``; the
client process is identified by its **working directory** instead (``lsof -a -p
<pid> -d cwd``, overridable through ``$JT_LSOF``), which is what
``GradleGameTracker`` does — search once per second until the client appears,
then only re-check liveness.

Machine-specific tool locations live here and nowhere else. The one hard-coded
absolute path is ``/usr/libexec/java_home``, the macOS JDK lookup helper,
overridable through ``$JT_JAVA_HOME_CMD``; ``lsof`` (needed to name the client
process on the gradle path) is resolved from ``$JT_LSOF`` and then ``$PATH``.
Both are platform tool paths, not project paths, and they are the only way to
honour the env's ``java.version`` and to identify the client on this machine.
"""

from __future__ import annotations

import os
import shlex
import shutil
import signal
import subprocess
import time
from pathlib import Path

from . import paths
from .errors import ConfigError, JtError, ToolMissingError
from .pack import modset_hash as _modset_hash
from .util import atomic_write_json, atomic_write_text, load_json, now_iso, sanitize_name

PRISM_LAUNCHER_ENV = "JT_PRISM_LAUNCHER"
JAVA_HOME_CMD_ENV = "JT_JAVA_HOME_CMD"
JAVA_HOME_ENV_TEMPLATE = "JT_JAVA_{version}_HOME"
# The one absolute path in this codebase: macOS's JDK lookup helper.
MACOS_JAVA_HOME_CMD = "/usr/libexec/java_home -v {version}"


def java_version_queries(version):
    """The version strings to hand the platform JDK helper, best first.

    macOS's ``java_home`` matches on the release string, so Java 8 answers to
    ``1.8`` and *not* to ``8`` — and a ``-v 8`` that matches nothing does not
    fail, it silently prints the default (newest) JDK. Asking ``1.8`` first for
    the 1.x era is what keeps a Java 8 env from being handed a Java 25 runtime.
    """
    yield str(version)
    if version <= 8:
        yield f"1.{version}"


INSTANCE_CFG = "instance.cfg"
PACK_JSON = "mmc-pack.json"
GAME_DIR_CANDIDATES = ("minecraft", ".minecraft")
LOG_NAME = "latest.log"
STATE_VERSION = 1
IMPORT_TIMEOUT_SEC = 1800
IMPORT_GRACE_SEC = 2.0

# The Gradle backend's contract with the checkout's build files: the wrapper it
# runs, and the project properties the checkout's run configuration reads to
# point the client at the harness's game directory, at a world to join, and at
# the in-client UI test harness. They are properties (not environment variables)
# so a plain `./gradlew runClient` without them keeps the checkout's own default
# behaviour.
GRADLE_WRAPPER = "gradlew"
GRADLE_RUN_DIR_PROPERTY = "jechRunDir"
GRADLE_QUICK_PLAY_PROPERTY = "jechQuickPlayWorld"
# ``-PjechTest=<selection>`` is what switches the checkout's UI test harness on
# and picks the scenarios to run; it is the *only* gate on that wiring, so a run
# without it never loads the harness mod at all.
GRADLE_UITEST_PROPERTY = "jechTest"
# How a Gradle-launched client is identified in the process table: the client's
# *working directory* is the game directory the run was given (both loaders launch
# it there), and that is the only per-run fact available. The client's own argv
# cannot carry one — ModDevGradle passes every JVM and program argument through
# ``@argfile`` files, which the process table does not show — and a name-based
# rule ("a java descendant that is not Gradle") matched Gradle's own short-lived
# toolchain probe, which made the runner read a vanished probe as "the game
# exited" and abandon a run that had not launched anything yet.
LSOF_ENV = "JT_LSOF"
MIN_GRADLE_GAME_SEARCH_SEC = 1.0


# The Prism/MultiMC component uid that carries each loader, and whether the
# component version is the bare loader version or the ``<mc>-<loader>`` string
# (the same split the mrpack dependency table in envs.py uses).
LOADER_COMPONENT_UIDS = {
    "forge": "net.minecraftforge",
    "neoforge": "net.neoforged",
    "fabric": "net.fabricmc.fabric-loader",
    "quilt": "org.quiltmc.quilt-loader",
}
LOADER_COMPONENT_NAMES = {
    "forge": "Forge",
    "neoforge": "NeoForge",
    "fabric": "Fabric Loader",
    "quilt": "Quilt Loader",
}
PACK_FORMAT_VERSION = 1

LOGGED_OFF_HINT = (
    "Prism must already have a Minecraft account configured in this data root "
    "(jt never touches credentials); a launcher log tail is kept with the run"
)


class InstanceInfo:
    def __init__(self, *, instance_id, root, reused, imported, modset_hash, pack_hash, cfg=None):
        self.instance_id = instance_id
        self.root = Path(root)
        self.reused = bool(reused)
        self.imported = bool(imported)
        self.modset_hash = modset_hash
        self.pack_hash = pack_hash
        self.cfg = cfg or {}

    def __repr__(self):
        return (f"InstanceInfo({self.instance_id!r}, reused={self.reused}, "
                f"imported={self.imported})")


class IsolationReport:
    def __init__(self, *, mods_dir, removed, staged, config_archived, config_archive,
                 saves_cleared, logs_cleared, overrides_staged=(), notes=()):
        self.mods_dir = mods_dir
        self.removed = removed
        self.staged = list(staged)
        self.config_archived = config_archived
        self.config_archive = config_archive
        self.saves_cleared = saves_cleared
        self.logs_cleared = logs_cleared
        # Non-mod overrides re-staged from the pack: "options.txt", "saves/<world>".
        self.overrides_staged = list(overrides_staged)
        self.notes = list(notes)

    def to_json(self):
        return {
            "modsDir": str(self.mods_dir),
            "modsRemoved": self.removed,
            "modsStaged": self.staged,
            "configArchived": self.config_archived,
            "configArchive": str(self.config_archive) if self.config_archive else None,
            "savesCleared": self.saves_cleared,
            "logsCleared": self.logs_cleared,
            "overridesStaged": self.overrides_staged,
            "notes": self.notes,
        }

    def summary(self):
        staged = f", overrides {', '.join(self.overrides_staged)}" if self.overrides_staged else ""
        return (f"mods: -{self.removed} +{len(self.staged)}, config archived "
                f"{self.config_archived} file(s), saves cleared {self.saves_cleared}, "
                f"logs cleared {self.logs_cleared}{staged}")


# ---------------------------------------------------------------------------
# locations
# ---------------------------------------------------------------------------


def prism_launcher():
    """The Prism CLI executable: ``$JT_PRISM_LAUNCHER`` first, then ``$PATH``."""
    configured = os.environ.get(PRISM_LAUNCHER_ENV)
    if configured:
        candidate = Path(os.path.expanduser(configured))
        if not candidate.is_file():
            raise ToolMissingError(
                f"{PRISM_LAUNCHER_ENV} points at {configured!r}, which is not an executable file"
            )
        return str(candidate)
    found = shutil.which("prismlauncher")
    if found:
        return found
    raise ToolMissingError(
        "prismlauncher was not found on PATH. Install Prism Launcher, or set "
        f"{PRISM_LAUNCHER_ENV} to its command-line executable — on macOS that binary lives "
        "inside the application bundle, under Contents/MacOS/prismlauncher."
    )


def instances_dir(root=None):
    return paths.instances_dir(root)


def instance_dir(instance_id, root=None):
    return instances_dir(root) / instance_id


def game_dir(instance_id, root=None):
    """The instance's game directory: modern Prism uses ``minecraft``."""
    base = instance_dir(instance_id, root)
    for name in GAME_DIR_CANDIDATES:
        candidate = base / name
        if candidate.is_dir():
            return candidate
    return base / GAME_DIR_CANDIDATES[0]


def instance_cfg_path(instance_id, root=None):
    return instance_dir(instance_id, root) / INSTANCE_CFG


def latest_log_path(instance_id, root=None):
    return game_dir(instance_id, root) / "logs" / LOG_NAME


def log_path_in(minecraft_dir):
    """``<game dir>/logs/latest.log`` for a game directory the caller owns.

    The Prism backend asks :func:`latest_log_path` (it has an instance id); the
    Gradle backend only has a directory, so both end up reading the same file.
    """
    return Path(minecraft_dir) / "logs" / LOG_NAME


def run_game_dir(run_dir):
    """The game directory a Gradle-backed run gets: ``<runs>/<id>/game``.

    Under the run's own directory on purpose: the game directory is an input the
    harness owns and rewrites on every run (mods, config, saves, options.txt), and
    a per-run location keeps it out of the checkout while leaving no state behind
    for the next run to inherit.
    """
    return Path(run_dir) / "game"


def list_instances(root=None):
    """``{instance id: directory}`` for every instance in this data root."""
    directory = instances_dir(root)
    found = {}
    if not directory.is_dir():
        return found
    for entry in sorted(directory.iterdir()):
        if entry.is_dir() and not entry.name.startswith(".") and (entry / INSTANCE_CFG).is_file():
            found[entry.name] = entry
    return found


# ---------------------------------------------------------------------------
# instance.cfg
# ---------------------------------------------------------------------------


def read_cfg(instance_id, root=None):
    path = instance_cfg_path(instance_id, root)
    if not path.is_file():
        return ""
    return path.read_text(encoding="utf-8", errors="replace")


def parse_cfg(text):
    """Prism's ``instance.cfg`` is a flat key=value file; values are raw."""
    values = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, _, value = stripped.partition("=")
        values[key.strip()] = value
    return values


def set_cfg_values(text, values):
    """Rewrite ``key=value`` lines, appending the ones that are absent.

    Prism reads everything after ``=`` verbatim (``JvmArgs`` is split by the
    launcher's own argument splitter), so values are written unquoted and any
    surrounding quotes the file already had are dropped.
    """
    lines = text.splitlines()
    remaining = dict(values)
    out = []
    for line in lines:
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and "=" in stripped:
            key = stripped.partition("=")[0].strip()
            if key in remaining:
                out.append(f"{key}={remaining.pop(key)}")
                continue
        out.append(line)
    had_trailing_newline = text.endswith("\n") or not text
    for key, value in remaining.items():
        out.append(f"{key}={value}")
    result = "\n".join(entry for entry in out if entry is not None)
    if had_trailing_newline or result:
        result += "\n"
    return result


def cfg_values_for(env, *, java_path):
    width, height = env.jvm.window
    return {
        "JavaPath": str(java_path),
        "OverrideJavaLocation": "true",
        "JvmArgs": env.jvm.jvm_args(),
        "OverrideJavaArgs": "true",
        "MinMemAlloc": str(env.jvm.min_mem_mb),
        "MaxMemAlloc": str(env.jvm.max_mem_mb),
        "OverrideMemory": "true",
        "MinecraftWinWidth": str(width),
        "MinecraftWinHeight": str(height),
        "OverrideWindow": "true",
        "LaunchMaximized": "false",
    }


def configure_instance(instance_id, env, *, root=None, log=None, java_home=None):
    """Apply the env's Java, memory and window settings to the instance."""
    home = Path(java_home) if java_home else java_home_for(env.java_version)
    java_path = home / "bin" / "java"
    if not java_path.is_file():
        raise ConfigError(
            f"java {env.java_version}: {paths.display(java_path)} does not exist "
            f"(resolved via {'$' + JAVA_HOME_CMD_ENV} or the platform JDK lookup)"
        )
    path = instance_cfg_path(instance_id, root)
    original = path.read_text(encoding="utf-8", errors="replace") if path.is_file() else ""
    values = cfg_values_for(env, java_path=java_path)
    updated = set_cfg_values(original, values)
    atomic_write_text(path, updated)
    if log:
        log(f"instance {instance_id}: java={paths.display(java_path)} "
            f"mem={values['MinMemAlloc']}-{values['MaxMemAlloc']}MB "
            f"window={values['MinecraftWinWidth']}x{values['MinecraftWinHeight']}")
    return dict(values)


# ---------------------------------------------------------------------------
# java lookup
# ---------------------------------------------------------------------------


def java_home_for(version, *, require_exists=True, command=None):
    """The JDK home for ``version`` (a major version such as 21).

    ``$JT_JAVA_HOME_CMD`` overrides the lookup; it must contain ``{version}``.
    On macOS the default is the platform's ``java_home`` helper; elsewhere the
    env vars ``JT_JAVA_<N>_HOME`` and a matching ``JAVA_HOME`` are consulted.
    """
    command = command or os.environ.get(JAVA_HOME_CMD_ENV) or MACOS_JAVA_HOME_CMD
    command = str(command)
    helper = shlex.split(command)[0] if command.strip() else ""
    if os.path.exists(helper) or JAVA_HOME_CMD_ENV in os.environ:
        tried = []
        for query in java_version_queries(version):
            rendered = command.format(version=query)
            argv = shlex.split(rendered)
            try:
                completed = subprocess.run(argv, capture_output=True, text=True, timeout=60,
                                           check=False)
            except (OSError, subprocess.SubprocessError) as exc:
                raise ConfigError(f"java {version}: cannot run {rendered!r} ({exc})")
            home = (completed.stdout.strip().splitlines()[0].strip()
                    if completed.stdout.strip() else "")
            if completed.returncode != 0 or not home:
                tried.append(f"{rendered!r} returned {completed.returncode} ({home or 'no output'})")
                continue
            candidate = Path(home)
            if require_exists and not (candidate / "bin" / "java").is_file():
                tried.append(f"{rendered!r} -> {paths.display(candidate)} (no bin/java)")
                continue
            # The helper answers with a *path*, and some of them fall back to the
            # default JDK when nothing matches. Verify the major version instead
            # of trusting the request, so a Java 8 env can never silently be
            # handed a Java 25 runtime.
            found = read_java_major(candidate)
            if found is not None and found != version:
                tried.append(f"{rendered!r} -> {paths.display(candidate)} (that is JDK {found})")
                continue
            return candidate
        raise ConfigError(
            f"java {version}: no JDK {version} found. "
            + ("; ".join(tried) if tried else "the platform lookup produced nothing")
            + f". Install a JDK {version}, or set "
            + JAVA_HOME_ENV_TEMPLATE.format(version=version) + f" (or {JAVA_HOME_CMD_ENV})."
        )

    key = JAVA_HOME_ENV_TEMPLATE.format(version=version)
    configured = os.environ.get(key) or os.environ.get("JAVA_HOME")
    if configured:
        candidate = Path(os.path.expanduser(configured))
        found = read_java_major(candidate)
        if found == version or found is None:
            if require_exists and not (candidate / "bin" / "java").is_file():
                raise ConfigError(f"java {version}: {configured!r} holds no bin/java executable")
            return candidate
        raise ConfigError(
            f"java {version}: {key}/JAVA_HOME points at a JDK {found}, not {version}"
        )
    raise ConfigError(
        f"java {version}: cannot resolve a JDK home. This platform has no usable "
        "/usr/libexec/java_home lookup; set " + key + f", or JAVA_HOME, or {JAVA_HOME_CMD_ENV}."
    )


def read_java_major(home):
    """The major version of a JDK home, from its ``release`` file when present."""
    release = Path(home) / "release"
    if not release.is_file():
        return None
    text = release.read_text(encoding="utf-8", errors="replace")
    for line in text.splitlines():
        if line.startswith("JAVA_VERSION="):
            value = line.partition("=")[2].strip().strip('"')
            major = value.split(".")[0] if not value.startswith("1.") else value.split(".")[1]
            try:
                return int(major)
            except ValueError:
                return None
    return None


# ---------------------------------------------------------------------------
# state.json
# ---------------------------------------------------------------------------


def read_state():
    path = paths.state_path()
    if not path.is_file():
        return {"version": STATE_VERSION, "envs": {}}
    try:
        state = load_json(path, what="state.json")
    except JtError:
        # A corrupt state file costs one re-import, never a failed run.
        return {"version": STATE_VERSION, "envs": {}, "warning": f"state.json was unreadable"}
    if not isinstance(state, dict) or not isinstance(state.get("envs"), dict):
        return {"version": STATE_VERSION, "envs": {}}
    state["version"] = STATE_VERSION
    return state


def write_state(state):
    atomic_write_json(paths.state_path(), state)
    return paths.state_path()


def modset_hash(env, resolved):
    return _modset_hash(env, resolved)


def instance_id_for(env, digest):
    """The deterministic Prism instance id for one env and one modset.

    The id is the env name plus the modset digest, so the same (env, modset)
    always maps to the same instance directory and a changed modset maps to a
    different one. That is what makes the import idempotent: a run interrupted
    mid-import leaves a directory whose name the next run can recognise and
    clean before re-importing, and a stale instance can never be silently
    reused for a different mod set.
    """
    return sanitize_name(f"{env.name}-{digest[:12]}")


def ensure_instance(env, resolved, pack_result, *, root=None, refresh=False, log=print):
    """Reuse the env's instance when its modset is unchanged, else import anew."""
    root = Path(root) if root else paths.prism_root()
    root.mkdir(parents=True, exist_ok=True)
    state = read_state()
    entry = (state.get("envs") or {}).get(env.name) or {}
    digest = modset_hash(env, resolved)
    desired_id = instance_id_for(env, digest)
    if not refresh and entry.get("instance_id") == desired_id and entry.get("modset_hash") == digest:
        existing = instance_dir(desired_id, root)
        if (existing / INSTANCE_CFG).is_file():
            # Re-apply the env's java/memory/window values even on reuse: the env
            # file is the authority for those, and it may have changed without
            # changing the modset (which is what keys the reuse).
            cfg = configure_instance(desired_id, env, root=root, log=log)
            log(f"instance {desired_id}: reused (modset {digest[:12]} unchanged)")
            return InstanceInfo(instance_id=desired_id, root=root, reused=True,
                                imported=False, modset_hash=digest,
                                pack_hash=pack_result.pack_hash, cfg=cfg)
        log(f"instance {desired_id}: recorded but missing on disk, re-creating")
    elif entry.get("instance_id"):
        log(f"instance {entry['instance_id']}: modset changed "
            f"({str(entry.get('modset_hash'))[:12]} -> {digest[:12]}) or naming changed, "
            f"importing a fresh instance {desired_id}")

    # A directory at the target id that state.json does not point at is debris
    # from an instance that was never recorded (an interrupted creation). Prism
    # would then refuse the id, so the debris is removed here, in python — never
    # with a shell rm that may be denied.
    leftover = instance_dir(desired_id, root)
    if leftover.exists() and entry.get("instance_id") != desired_id:
        log(f"instance {desired_id}: leftover directory not recorded in state.json; "
            "removing it before re-creating the instance")
        shutil.rmtree(leftover, ignore_errors=True)

    instance_id = materialize_instance(env, desired_id, root=root, log=log)
    cfg = cfg_values_for(env, java_path=java_home_for(env.java_version) / "bin" / "java")
    state.setdefault("envs", {})[env.name] = {
        "instance_id": instance_id,
        "modset_hash": digest,
        "pack_hash": pack_result.pack_hash,
        "mc": env.mc,
        "loader": {"type": env.loader.type, "version": env.loader.version},
        "java": env.java_version,
        "updated_at": now_iso(),
    }
    write_state(state)
    log(f"state: {paths.display(paths.state_path())} maps {env.name} -> {instance_id}")
    return InstanceInfo(instance_id=instance_id, root=root, reused=False, imported=True,
                        modset_hash=digest, pack_hash=pack_result.pack_hash, cfg=cfg)


def component_version(env, uid):
    """The version string Prism's metadata expects for one component.

    The two loaders that ship an ``<mc>-<loader>`` string (legacy Forge) differ
    from the mrpack dependency spelling: the pack index wants
    ``1.12.2-14.23.5.2864``, while ``net.minecraftforge``'s component is
    ``14.23.5.2864`` and a prefixed one 404s against Prism's metadata.
    """
    version = env.loader.version
    if uid == "net.minecraftforge":
        prefix = f"{env.mc}-"
        if version.startswith(prefix):
            version = version[len(prefix):]
    return version


def pack_json(env):
    """The ``mmc-pack.json`` body for one env: Minecraft plus its loader.

    Prism resolves the rest of the chain (LWJGL, assets, natives) from these two
    components, so a run needs no pack import at all. The ``cached*`` keys are
    what Prism itself writes and what its version listing reads without a
    download; omitting them leaves the instance nameless in the launcher.
    """
    try:
        uid = LOADER_COMPONENT_UIDS[env.loader.type]
    except KeyError:
        raise ConfigError(
            f"loader {env.loader.type!r} has no Prism component uid "
            f"(known: {', '.join(sorted(LOADER_COMPONENT_UIDS))})"
        )
    version = component_version(env, uid)
    return {
        "formatVersion": PACK_FORMAT_VERSION,
        "components": [
            {"uid": "net.minecraft", "version": env.mc,
             "cachedName": "Minecraft", "cachedVersion": env.mc, "important": True},
            {"uid": uid, "version": version,
             "cachedName": LOADER_COMPONENT_NAMES[env.loader.type],
             "cachedVersion": version,
             "cachedRequires": [{"uid": "net.minecraft", "equals": env.mc}]},
        ],
    }


def materialize_instance(env, instance_id, *, root=None, java_home=None, log=print):
    """Create the instance directory directly, then return its id.

    jt used to hand a local ``.mrpack`` to ``prismlauncher -I`` and diff the
    instances directory. On Prism 11 that path goes through
    ``MainWindow::addInstance()`` and opens the modal New Instance dialog, and
    the CLI keeps the main window open afterwards instead of exiting — a
    headless run can never complete it. Writing the two files Prism reads
    (``instance.cfg`` and ``mmc-pack.json``) is the same thing the import does
    without its UI: Prism's own launch task resolves and downloads whatever the
    version chain still needs. Mods are staged by :func:`isolate`, not here.
    """
    root = Path(root) if root else paths.prism_root()
    root.mkdir(parents=True, exist_ok=True)
    directory = instance_dir(instance_id, root)
    if directory.exists():
        # Callers clear debris first; anything still here would be overwritten.
        raise ConfigError(f"instance {instance_id}: {paths.display(directory)} already exists")
    minecraft = directory / GAME_DIR_CANDIDATES[0]
    (minecraft / "mods").mkdir(parents=True, exist_ok=True)

    home = Path(java_home) if java_home else java_home_for(env.java_version)
    java = home / "bin" / "java"
    if not java.is_file():
        raise ConfigError(
            f"java {env.java_version}: {paths.display(java)} does not exist "
            f"(resolved via {'$' + JAVA_HOME_CMD_ENV} or the platform JDK lookup)"
        )
    values = {"ConfigVersion": "1.3", "InstanceType": "OneSix", "iconKey": "default",
              "name": instance_id, "AutomaticJava": "false"}
    values.update(cfg_values_for(env, java_path=java))
    text = "[General]\n" + "\n".join(f"{key}={value}" for key, value in values.items()) + "\n"
    atomic_write_text(directory / INSTANCE_CFG, text)
    atomic_write_json(directory / PACK_JSON, pack_json(env))
    log(f"instance {instance_id}: materialized (mc {env.mc}, {env.loader.type} "
        f"{env.loader.version}, java {paths.display(home)})")
    return instance_id


# ---------------------------------------------------------------------------
# isolation
# ---------------------------------------------------------------------------


def clear_dir(path):
    """Delete the *contents* of a directory; returns how many entries went."""
    path = Path(path)
    if not path.exists():
        path.mkdir(parents=True, exist_ok=True)
        return 0
    if not path.is_dir():
        path.unlink()
        path.mkdir(parents=True, exist_ok=True)
        return 1
    removed = 0
    for entry in sorted(path.iterdir()):
        if entry.is_dir() and not entry.is_symlink():
            shutil.rmtree(entry)
        else:
            entry.unlink()
        removed += 1
    return removed


def archive_dir(source, dest):
    """Copy a directory tree to ``dest`` (for evidence), then empty ``source``."""
    source = Path(source)
    dest = Path(dest)
    if not source.is_dir():
        return 0
    if dest.exists():
        shutil.rmtree(dest)
    shutil.copytree(source, dest, symlinks=True)
    count = sum(1 for p in dest.rglob("*") if p.is_file())
    clear_dir(source)
    return count


def isolate(instance_id, resolved, run_dir, *, root=None, pack_result=None, game_dir_path=None,
            log=print):
    """Make the instance match the pack exactly, discarding every previous run.

    Every run re-syncs ``mods/`` from the pack (delete-then-stage), archives then
    empties ``config/``, empties ``saves/`` and ``logs/``, and re-stages the pack's
    non-mod overrides — the ``options.txt`` preset and the env's world seed — into
    the game directory. Nothing here depends on the previous run's state, so a
    stale instance can never influence a verdict: a world a previous run played in
    is replaced by the seed, and an ``options.txt`` the game rewrote is replaced by
    the preset.

    ``game_dir_path`` overrides where that directory is. The Prism backend leaves
    it out (the instance id decides it); the Gradle backend has no instance and
    passes the directory the run was given.
    """
    run_dir = Path(run_dir)
    mc = Path(game_dir_path) if game_dir_path is not None else game_dir(instance_id, root)
    notes = []

    # Only the artifacts that travel as files: an artifact the checkout's Gradle
    # run already carries on its classpath (``via: dev-classpath``) must not be
    # copied here, or the loader would see the same mod id twice.
    staged_artifacts = resolved.staged
    mods_dir = mc / "mods"
    removed = clear_dir(mods_dir)
    staged = []
    for artifact in staged_artifacts:
        if artifact.path is None or not artifact.path.is_file():
            raise ConfigError(f"{artifact.name}: {artifact.filename} is missing from the cache")
        shutil.copy2(artifact.path, mods_dir / artifact.filename)
        staged.append(artifact.filename)
    expected = {artifact.filename for artifact in staged_artifacts}
    present = {p.name for p in mods_dir.iterdir() if p.is_file()}
    if present != expected:
        notes.append(f"mods/ after sync holds {sorted(present)} (expected {sorted(expected)})")
    for artifact in resolved.artifacts:
        if not artifact.staged:
            notes.append(f"{artifact.name} ({artifact.filename}) rides the checkout's classpath "
                         "(via=dev-classpath), not staged into mods/")

    config_archive = run_dir / "pre-run-config"
    archived = archive_dir(mc / "config", config_archive)
    if archived == 0:
        notes.append("no config/ content existed before the run")
        if not config_archive.exists():
            config_archive = None

    saves_cleared = clear_dir(mc / "saves")
    logs_cleared = clear_dir(mc / "logs")

    overrides_staged = []
    if pack_result is not None:
        overrides_staged = stage_override_tree(mc, pack_result, log=log)

    report = IsolationReport(
        mods_dir=mods_dir, removed=removed, staged=staged, config_archived=archived,
        config_archive=config_archive, saves_cleared=saves_cleared, logs_cleared=logs_cleared,
        overrides_staged=overrides_staged, notes=notes,
    )
    log(f"isolation [{instance_id}]: {report.summary()}")
    for note in notes:
        log(f"isolation [{instance_id}]: note: {note}")
    return report


def stage_override_tree(minecraft_dir, pack_result, *, log=print):
    """Copy the pack's non-mod overrides into the game directory.

    A file override is copied over; a directory override (the world seed) replaces
    whatever is there, so the staged tree is exactly the pack's tree and never a
    merge of two worlds.
    """
    staged = []
    source_root = pack_result.overrides_dir()
    for relative in pack_result.extra_overrides:
        source = source_root / relative
        dest = Path(minecraft_dir) / relative
        if not source.exists():
            raise ConfigError(
                f"the pack declares override {relative!r} but {paths.display(source)} does not exist"
            )
        if source.is_dir():
            if dest.exists():
                shutil.rmtree(dest)
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(source, dest, symlinks=True)
            copied = sum(1 for entry in dest.rglob("*") if entry.is_file())
            log(f"isolation: staged {relative} ({copied} file(s)) from the pack")
        else:
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, dest)
            log(f"isolation: staged {relative} from the pack")
        staged.append(relative)
    return staged


# ---------------------------------------------------------------------------
# process tracking
# ---------------------------------------------------------------------------


def process_table():
    """``{pid: (ppid, command line)}`` from ``ps`` — portable on macOS and Linux."""
    try:
        completed = subprocess.run(["ps", "-Ao", "pid=,ppid=,command="], capture_output=True,
                                   text=True, timeout=30, check=False)
    except (OSError, subprocess.SubprocessError) as exc:
        raise JtError(f"cannot read the process table via ps ({exc})")
    table = {}
    for line in completed.stdout.splitlines():
        parts = line.strip().split(None, 2)
        if len(parts) < 2:
            continue
        try:
            pid, ppid = int(parts[0]), int(parts[1])
        except ValueError:
            continue
        table[pid] = (ppid, parts[2] if len(parts) > 2 else "")
    return table


def game_pids(instance_id, *, root=None, exclude=()):
    """Pids of the game JVM for this instance.

    Matched on the instance's own directory appearing in the command line, not on
    a name pattern: ``pgrep -f`` also matches the searching process, which is
    exactly the trap the pack-lifecycle skill warns about. The *game directory*
    is the wrong key: Prism hands the JVM ``-Djava.library.path=<instance>/natives``
    and ``--gameDir`` only for some loaders, so a match on ``<instance>/minecraft``
    finds nothing and the harness reads a running game as "never started". Both
    the instance directory and its game directory are therefore accepted.
    """
    keys = {str(instance_dir(instance_id, root))}
    game = str(game_dir(instance_id, root))
    if game not in keys:
        keys.add(game)
    excluded = {int(pid) for pid in exclude} | {os.getpid()}
    pids = []
    for pid, (_ppid, command) in process_table().items():
        if pid in excluded or not any(key in command for key in keys):
            continue
        lowered = command.casefold()
        if "java" in lowered or "prism" in lowered:
            pids.append(pid)
    return sorted(pids)


def lsof_command():
    """The ``lsof`` executable: ``$JT_LSOF`` first, then ``$PATH``.

    lsof is how a process's working directory is read; it is a base-system tool on
    macOS and Linux (where this harness runs), and it can read the directory of a
    process this user owns without elevation, which ``ps`` cannot report at all.
    """
    configured = os.environ.get(LSOF_ENV)
    if configured:
        candidate = Path(os.path.expanduser(configured))
        if not candidate.is_file():
            raise ToolMissingError(
                f"{LSOF_ENV} points at {configured!r}, which is not an executable file"
            )
        return str(candidate)
    found = shutil.which("lsof")
    if found:
        return found
    raise ToolMissingError(
        "lsof was not found on PATH. It is how the harness reads a Gradle-launched "
        f"client's working directory (the only per-run identity available); set {LSOF_ENV} "
        "to its path if it is not on PATH."
    )


def cwd_of(pid, *, command=None):
    """The working directory of a process, or ``None`` when it cannot be read."""
    argv = shlex.split(command) if command else [lsof_command()]
    argv += ["-a", "-p", str(int(pid)), "-d", "cwd"]
    try:
        completed = subprocess.run(argv, capture_output=True, text=True, timeout=30, check=False)
    except (OSError, subprocess.SubprocessError) as exc:
        raise JtError(f"cannot read the working directory of pid {pid} ({exc})")
    lines = [line for line in completed.stdout.splitlines() if line.strip()]
    if len(lines) < 2:
        return None
    # ``COMMAND PID USER FD TYPE DEVICE SIZE/OFF NODE NAME`` — NAME is the tail and
    # may itself contain spaces, so it is taken with a bounded split.
    fields = lines[-1].split(None, 8)
    if len(fields) < 9:
        return None
    return fields[8]


def pid_alive(pid):
    """Whether a pid still exists (same signal-0 probe :func:`kill_process_tree` uses)."""
    try:
        os.kill(int(pid), 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True


def gradle_game_pids(launch_pid, game_dir, *, command=None):
    """Pids of the game JVM a Gradle run task started, matched on its working directory.

    :func:`game_pids` matches the *instance directory* in a process's command line;
    a Gradle run has no such argument to match (see the module comments). What it
    does have is the client's working directory, which both loaders set to the game
    directory the run was given — so the game is "a java process under this
    launcher whose cwd is this run's game directory", the same *kind* of evidence
    the Prism backend uses (a path only this run has).

    A name heuristic was tried first and was wrong in a way that mattered: "any
    java descendant that is not Gradle" also matched the short-lived toolchain
    probe Gradle forks while configuring, so the runner saw a pid, recorded "the
    game started", watched the probe disappear and stopped the run as if the client
    had exited — before the client was even launched. The launcher, the client and
    everything else were then left running.
    """
    wanted = str(Path(game_dir))
    table = process_table()
    candidates = descendants([int(launch_pid)], table) - {os.getpid(), int(launch_pid)}
    pids = []
    for pid in sorted(candidates):
        command_line = table.get(pid, (0, ""))[1]
        if "java" not in command_line.casefold():
            continue
        cwd = cwd_of(pid, command=command)
        if cwd is None:
            continue
        # The launcher hands the client the directory, not a normalised form of it:
        # compare resolved paths so a trailing separator or a symlink in the middle
        # cannot hide the game.
        if _same_dir(cwd, wanted):
            pids.append(pid)
    return pids


def _same_dir(left, right):
    try:
        return Path(left).resolve() == Path(right).resolve()
    except OSError:  # pragma: no cover - resolve() is tolerant, but be safe
        return str(left) == str(right)


class GradleGameTracker:
    """Finds the Gradle-launched client once, then tracks it cheaply.

    The search costs an ``lsof`` per java candidate per poll, so it runs until the
    client is found (with a floor between attempts) and never again: after that the
    already-known pids are re-checked with signal 0. ``pids()`` is what the watch
    loop calls, so its answer has the same meaning as ``instance.game_pids`` on the
    Prism side.
    """

    def __init__(self, launch_pid, game_dir, *, command=None,
                 interval=MIN_GRADLE_GAME_SEARCH_SEC, clock=time.monotonic):
        self.launch_pid = int(launch_pid)
        self.game_dir = Path(game_dir)
        self.command = command
        self.interval = interval
        self.clock = clock
        self.found = []
        self.searches = 0
        self._last_search = None

    def pids(self):
        if self.found:
            self.found = [pid for pid in self.found if pid_alive(pid)]
            return list(self.found)
        now = self.clock()
        if self._last_search is not None and now - self._last_search < self.interval:
            return []
        self._last_search = now
        self.searches += 1
        self.found = gradle_game_pids(self.launch_pid, self.game_dir, command=self.command)
        return list(self.found)

    def __repr__(self):
        return (f"GradleGameTracker(launcher={self.launch_pid}, game_dir={self.game_dir}, "
                f"found={self.found})")


def descendants(pids, table=None):
    table = table if table is not None else process_table()
    wanted = set(int(pid) for pid in pids)
    changed = True
    while changed:
        changed = False
        for pid, (ppid, _command) in table.items():
            if ppid in wanted and pid not in wanted:
                wanted.add(pid)
                changed = True
    return wanted


def kill_process_tree(pids, *, log=print, grace=IMPORT_GRACE_SEC * 3):
    """SIGTERM the given pids and their descendants, then SIGKILL the survivors."""
    table = process_table()
    targets = descendants(pids, table)
    if not targets:
        return []
    log(f"watchdog: terminating {len(targets)} process(es): {sorted(targets)}")
    for pid in sorted(targets):
        _signal(pid, signal.SIGTERM)
    deadline = time.time() + grace
    while time.time() < deadline:
        alive = [pid for pid in targets if _alive(pid)]
        if not alive:
            return sorted(targets)
        time.sleep(0.2)
    for pid in sorted(targets):
        if _alive(pid):
            _signal(pid, signal.SIGKILL)
    return sorted(targets)


def _alive(pid):
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True


def _signal(pid, signum):
    try:
        os.kill(pid, signum)
    except (ProcessLookupError, PermissionError):
        pass


# ---------------------------------------------------------------------------
# launching and log tracking
# ---------------------------------------------------------------------------


def launch(instance_id, *, root=None, log_path, extra_args=(), world=None, log=print):
    """Start the instance in the background; returns the launcher Popen.

    ``world`` selects Prism's own *join world on launch* option (``-w/--world``),
    which is what makes the client load a world instead of stopping at the title
    screen. It is a first-class Prism option, not a game-argument passthrough:
    Prism 11 builds the version-appropriate game argument for it itself (it knows
    ``--quickPlaySingleplayer``), whereas a bare ``-- <args>`` is parsed by Qt as
    "everything after this is a positional url" and turns the trailing arguments
    into resource *imports*, which is not what a launch wants.
    """
    root = Path(root) if root else paths.prism_root()
    exe = prism_launcher()
    command = [exe, "-d", str(root), "--launch", instance_id]
    if world:
        command.extend(["--world", world])
    command.extend(extra_args)
    Path(log_path).parent.mkdir(parents=True, exist_ok=True)
    handle = open(log_path, "ab", buffering=0)
    kwargs = {}
    if os.name == "posix":
        kwargs["start_new_session"] = True
    proc = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=handle, stderr=subprocess.STDOUT,
                            **kwargs)
    log(f"launch: {' '.join(command)} (pid {proc.pid}, log {paths.display(log_path)})")
    return proc


def gradle_command(checkout, task, game_dir, *, world=None, uitest=None, wrapper=GRADLE_WRAPPER):
    """The ``./gradlew`` argv that runs one env's client inside its checkout.

    ``--no-daemon``: a resident Gradle daemon would keep a several-hundred-megabyte
    JVM alive between runs, which this machine's memory budget does not want.

    The game directory, the world and the UI test selection are handed over as
    **project properties**, not as game arguments: the checkout's own run
    configuration is what turns them into ``gameDirectory``/``--quickPlaySingleplayer``/
    ``-Djech.uitest.run`` for whichever loader the module uses. That is also why the
    property names live in the checkout's build files — see the ``gradle`` backend
    section of README.md.
    """
    command = [str(Path(checkout) / wrapper), task, "--no-daemon",
               f"-P{GRADLE_RUN_DIR_PROPERTY}={game_dir}"]
    if world:
        command.append(f"-P{GRADLE_QUICK_PLAY_PROPERTY}={world}")
    if uitest:
        command.append(f"-P{GRADLE_UITEST_PROPERTY}={uitest}")
    return command


def launch_gradle(checkout, task, game_dir, *, world=None, uitest=None, java_home=None, log_path,
                  extra_args=(), log=print):
    """Start the checkout's Gradle run task in the background; returns the Popen.

    The client's JDK is *not* chosen here — the checkout's own toolchain decides
    that. ``java_home`` is the JDK that runs Gradle itself, set only when the env
    asks for one (a wrapper too old for the machine's default JDK).

    Gradle's own output goes to ``log_path``; the game's log is the one the client
    writes under the game directory, which the caller follows separately.
    """
    checkout = Path(checkout)
    command = gradle_command(checkout, task, game_dir, world=world, uitest=uitest)
    command.extend(extra_args)
    Path(log_path).parent.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ)
    if java_home is not None:
        env["JAVA_HOME"] = str(java_home)
    handle = open(log_path, "ab", buffering=0)
    kwargs = {}
    if os.name == "posix":
        kwargs["start_new_session"] = True
    proc = subprocess.Popen(command, cwd=str(checkout), env=env, stdin=subprocess.DEVNULL,
                            stdout=handle, stderr=subprocess.STDOUT, **kwargs)
    log(f"launch: {' '.join(command)} (cwd {paths.display(checkout)}, pid {proc.pid}, "
        f"log {paths.display(log_path)})")
    return proc


def is_new_session(log_path, started_at, *, slack=1.0):
    """True when ``latest.log`` belongs to a session started at/after ``started_at``."""
    try:
        stat = os.stat(log_path)
    except OSError:
        return False
    return stat.st_mtime >= started_at - slack and stat.st_size > 0


class LogFollower:
    """Incremental reader for ``latest.log``.

    The file may still be the previous session's; a session is only accepted once
    its mtime proves it was (re)written after the run started, which is the trap
    the pack-lifecycle skill records. Reads are incremental so a long run does not
    re-read its own log, and the whole capture is kept for the checks and for
    evidence next to the report.
    """

    def __init__(self, log_path, started_at, *, poll=0.5):
        self.log_path = Path(log_path)
        self.started_at = started_at
        self.poll = poll
        self.text = ""
        self.session_started = False
        self.offsets = 0

    def wait_for_session(self, deadline, *, log=None, sleep=time.sleep):
        while time.time() < deadline:
            if is_new_session(self.log_path, self.started_at):
                self.session_started = True
                if log:
                    log(f"log: tracking {paths.display(self.log_path)}")
                return True
            sleep(self.poll)
        return False

    def poll_once(self):
        """Append whatever the game has written since the last read."""
        if not self.session_started or not self.log_path.is_file():
            return ""
        try:
            with open(self.log_path, "rb") as handle:
                handle.seek(self.offsets)
                data = handle.read()
                self.offsets = handle.tell()
        except OSError:
            return ""
        chunk = data.decode("utf-8", errors="replace")
        self.text += chunk
        return chunk

    def tail(self, chars=400):
        return self.text[-chars:]
