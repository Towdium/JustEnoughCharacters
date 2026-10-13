"""Prism Launcher management: data root, import, instance.cfg, reuse, isolation.

Everything here follows the measured Prism CLI behaviour recorded in the
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

The module owns exactly one absolute path: ``/usr/libexec/java_home``, the macOS
JDK lookup helper, overridable through ``$JT_JAVA_HOME_CMD``. It is a platform
tool path, not a project path, and it is the only way to honour the env's
``java.version`` on this machine.
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
from .util import atomic_write_json, atomic_write_text, load_json, now_iso

PRISM_LAUNCHER_ENV = "JT_PRISM_LAUNCHER"
JAVA_HOME_CMD_ENV = "JT_JAVA_HOME_CMD"
JAVA_HOME_ENV_TEMPLATE = "JT_JAVA_{version}_HOME"
# The one absolute path in this codebase: macOS's JDK lookup helper.
MACOS_JAVA_HOME_CMD = "/usr/libexec/java_home -v {version}"

INSTANCE_CFG = "instance.cfg"
GAME_DIR_CANDIDATES = ("minecraft", ".minecraft")
LOG_NAME = "latest.log"
STATE_VERSION = 1
IMPORT_TIMEOUT_SEC = 1800
IMPORT_GRACE_SEC = 2.0

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
                 saves_cleared, logs_cleared, notes=()):
        self.mods_dir = mods_dir
        self.removed = removed
        self.staged = list(staged)
        self.config_archived = config_archived
        self.config_archive = config_archive
        self.saves_cleared = saves_cleared
        self.logs_cleared = logs_cleared
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
            "notes": self.notes,
        }

    def summary(self):
        return (f"mods: -{self.removed} +{len(self.staged)}, config archived "
                f"{self.config_archived} file(s), saves cleared {self.saves_cleared}, "
                f"logs cleared {self.logs_cleared}")


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
        rendered = command.format(version=version)
        argv = shlex.split(rendered)
        try:
            completed = subprocess.run(argv, capture_output=True, text=True, timeout=60, check=False)
        except (OSError, subprocess.SubprocessError) as exc:
            raise ConfigError(f"java {version}: cannot run {rendered!r} ({exc})")
        home = completed.stdout.strip().splitlines()[0].strip() if completed.stdout.strip() else ""
        if completed.returncode != 0 or not home:
            raise ConfigError(
                f"java {version}: {rendered!r} returned {completed.returncode} and "
                f"{completed.stdout.strip()!r}; install a JDK {version} or set "
                f"{JAVA_HOME_CMD_ENV} (e.g. 'your-java-home-helper -v {{version}}')"
            )
        candidate = Path(home)
        if require_exists and not (candidate / "bin" / "java").is_file():
            raise ConfigError(f"java {version}: {home!r} holds no bin/java executable")
        return candidate

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


def ensure_instance(env, resolved, pack_result, *, root=None, refresh=False, log=print):
    """Reuse the env's instance when its modset is unchanged, else import anew."""
    root = Path(root) if root else paths.prism_root()
    root.mkdir(parents=True, exist_ok=True)
    state = read_state()
    entry = (state.get("envs") or {}).get(env.name) or {}
    digest = modset_hash(env, resolved)
    if not refresh and entry.get("instance_id") and entry.get("modset_hash") == digest:
        existing = instance_dir(entry["instance_id"], root)
        if (existing / INSTANCE_CFG).is_file():
            log(f"instance {entry['instance_id']}: reused (modset {digest[:12]} unchanged)")
            return InstanceInfo(instance_id=entry["instance_id"], root=root, reused=True,
                                imported=False, modset_hash=digest,
                                pack_hash=pack_result.pack_hash, cfg=parse_cfg(read_cfg(entry["instance_id"], root)))
        log(f"instance {entry['instance_id']}: recorded but missing on disk, re-importing")
    elif entry.get("instance_id"):
        log(f"instance {entry['instance_id']}: modset changed "
            f"({str(entry.get('modset_hash'))[:12]} -> {digest[:12]}), importing a fresh instance")

    instance_id = import_pack(pack_result.mrpack_path, root=root, log=log)
    cfg = configure_instance(instance_id, env, root=root, log=log)
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


def import_pack(mrpack_path, *, root=None, log=print, timeout=IMPORT_TIMEOUT_SEC, log_path=None):
    """Import a local ``.mrpack`` and return the id of the instance it created."""
    mrpack_path = Path(mrpack_path)
    if not mrpack_path.is_file():
        raise ConfigError(f"pack {paths.display(mrpack_path)} does not exist")
    root = Path(root) if root else paths.prism_root()
    root.mkdir(parents=True, exist_ok=True)
    exe = prism_launcher()
    before = set(list_instances(root))
    command = [exe, "-d", str(root), "-I", str(mrpack_path)]
    log(f"import: {' '.join(command)}")
    try:
        completed = subprocess.run(
            command, stdin=subprocess.DEVNULL, capture_output=True, text=True, timeout=timeout,
            check=False,
        )
    except subprocess.TimeoutExpired:
        raise ConfigError(
            f"prism import timed out after {timeout}s. Inline imports hang when Prism opens a "
            "dialog: check that the pack is a local file and that a display is available "
            "(the CLI needs a window server even for a headless import)."
        )
    output = (completed.stdout or "") + (completed.stderr or "")
    if log_path:
        atomic_write_text(log_path, output)
    after = list_instances(root)
    new_ids = sorted(set(after) - before)
    if len(new_ids) != 1:
        tail = "\n".join(output.strip().splitlines()[-20:])
        raise ConfigError(
            f"prism import created {len(new_ids)} new instance(s) (expected exactly 1); "
            f"exit code {completed.returncode}. Instances now present: "
            f"{', '.join(sorted(after)) or '(none)'}.\nimport output tail:\n{tail}"
            + (f"\nnote: {LOGGED_OFF_HINT}" if completed.returncode == 0 else "")
        )
    instance_id = new_ids[0]
    log(f"import: created instance {instance_id}")
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


def isolate(instance_id, resolved, run_dir, *, root=None, log=print):
    """Make the instance match the pack exactly, discarding every previous run.

    Every run re-syncs ``mods/`` from the pack (delete-then-stage), archives then
    empties ``config/`` and empties ``saves/`` and ``logs/``. Nothing here depends
    on the previous run's state, so a stale instance can never influence a verdict.
    """
    run_dir = Path(run_dir)
    mc = game_dir(instance_id, root)
    notes = []

    mods_dir = mc / "mods"
    removed = clear_dir(mods_dir)
    staged = []
    for artifact in resolved.artifacts:
        if artifact.path is None or not artifact.path.is_file():
            raise ConfigError(f"{artifact.name}: {artifact.filename} is missing from the cache")
        shutil.copy2(artifact.path, mods_dir / artifact.filename)
        staged.append(artifact.filename)
    expected = {artifact.filename for artifact in resolved.artifacts}
    present = {p.name for p in mods_dir.iterdir() if p.is_file()}
    if present != expected:
        notes.append(f"mods/ after sync holds {sorted(present)} (expected {sorted(expected)})")

    config_archive = run_dir / "pre-run-config"
    archived = archive_dir(mc / "config", config_archive)
    if archived == 0:
        notes.append("no config/ content existed before the run")
        if not config_archive.exists():
            config_archive = None

    saves_cleared = clear_dir(mc / "saves")
    logs_cleared = clear_dir(mc / "logs")

    report = IsolationReport(
        mods_dir=mods_dir, removed=removed, staged=staged, config_archived=archived,
        config_archive=config_archive, saves_cleared=saves_cleared, logs_cleared=logs_cleared,
        notes=notes,
    )
    log(f"isolation [{instance_id}]: {report.summary()}")
    for note in notes:
        log(f"isolation [{instance_id}]: note: {note}")
    return report


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

    Matched on the instance's own game directory appearing in the command line,
    not on a name pattern: ``pgrep -f`` also matches the searching process, which
    is exactly the trap the pack-lifecycle skill warns about.
    """
    key = str(game_dir(instance_id, root))
    excluded = {int(pid) for pid in exclude} | {os.getpid()}
    pids = []
    for pid, (_ppid, command) in process_table().items():
        if pid in excluded or key not in command:
            continue
        lowered = command.casefold()
        if "java" in lowered or "prism" in lowered:
            pids.append(pid)
    return sorted(pids)


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


def launch(instance_id, *, root=None, log_path, extra_args=(), log=print):
    """Start the instance in the background; returns the launcher Popen."""
    root = Path(root) if root else paths.prism_root()
    exe = prism_launcher()
    command = [exe, "-d", str(root), "--launch", instance_id]
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
