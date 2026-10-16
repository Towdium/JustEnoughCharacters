"""The game directory, its isolation, and the launch layer.

A run's client is started by the checkout's own Gradle run task
(``./gradlew <task> --no-daemon -PjechRunDir=<gameDir>``): the game directory is
handed to the build, the client boots inside it, and ``latest.log`` is read from
``<gameDir>/logs``. Gradle passes the client's own arguments through an
``@argfile``, so neither argv nor the environment is visible in ``ps``; the
client process is identified by its **working directory** instead (``lsof -a -p
<pid> -d cwd``, overridable through ``$JT_LSOF``), which is what
``GradleGameTracker`` does — search once per second until the client appears,
then only re-check liveness.

Isolation lives here too, because the game directory is an input the harness
*owns* rather than one it inherits: every run rebuilds it from the pack (``mods/``
re-synced, ``config/`` archived then emptied, ``saves/`` and ``logs/`` emptied,
the pack's non-mod overrides re-staged), so no state a previous run left behind
can influence a verdict.

Machine-specific tool locations live here and nowhere else. The one hard-coded
absolute path is ``/usr/libexec/java_home``, the macOS JDK lookup helper,
overridable through ``$JT_JAVA_HOME_CMD``; ``lsof`` (needed to name the client
process) is resolved from ``$JT_LSOF`` and then ``$PATH``. Both are platform tool
paths, not project paths, and they are the only way to honour the env's
``java.version`` and to identify the client on this machine.
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

JAVA_HOME_CMD_ENV = "JT_JAVA_HOME_CMD"
JAVA_HOME_ENV_TEMPLATE = "JT_JAVA_{version}_HOME"
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


LOG_NAME = "latest.log"

GRADLE_WRAPPER = "gradlew"
GRADLE_RUN_DIR_PROPERTY = "jechRunDir"
GRADLE_QUICK_PLAY_PROPERTY = "jechQuickPlayWorld"
GRADLE_UITEST_PROPERTY = "jechTest"
LSOF_ENV = "JT_LSOF"
MIN_GRADLE_GAME_SEARCH_SEC = 1.0
TERMINATION_GRACE_SEC = 6.0


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




def run_game_dir(run_dir):
    """The game directory a run gets: ``<runs>/<id>/game``.

    Under the run's own directory on purpose: the game directory is an input the
    harness owns and rewrites on every run (mods, config, saves, options.txt), and
    a per-run location keeps it out of the checkout while leaving no state behind
    for the next run to inherit.
    """
    return Path(run_dir) / "game"


def log_path_in(minecraft_dir):
    """``<game dir>/logs/latest.log`` for a game directory the caller owns."""
    return Path(minecraft_dir) / "logs" / LOG_NAME




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


def isolate(label, resolved, run_dir, *, game_dir_path, pack_result=None, log=print):
    """Make the game directory match the pack exactly, discarding every previous run.

    Every run re-syncs ``mods/`` from the pack (delete-then-stage), archives then
    empties ``config/``, empties ``saves/`` and ``logs/``, and re-stages the pack's
    non-mod overrides — the ``options.txt`` preset and the env's world seed — into
    the game directory. Nothing here depends on the previous run's state, so a
    stale directory can never influence a verdict: a world a previous run played in
    is replaced by the seed, and an ``options.txt`` the game rewrote is replaced by
    the preset.

    ``label`` is only the name the log lines carry (the env's run label);
    ``game_dir_path`` is the directory the run was given.
    """
    run_dir = Path(run_dir)
    mc = Path(game_dir_path)
    notes = []

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
    log(f"isolation [{label}]: {report.summary()}")
    for note in notes:
        log(f"isolation [{label}]: note: {note}")
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

    A Gradle run has no argument in the process table that names this run: the game
    is "a java process under this launcher whose cwd is this run's game directory",
    which both loaders set — so the identity is a path only this run has, never a
    name.

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
    loop calls.
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


def kill_process_tree(pids, *, log=print, grace=TERMINATION_GRACE_SEC):
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




def gradle_command(checkout, task, game_dir, *, world=None, uitest=None, wrapper=GRADLE_WRAPPER):
    """The ``./gradlew`` argv that runs one env's client inside its checkout.

    ``--no-daemon``: a resident Gradle daemon would keep a several-hundred-megabyte
    JVM alive between runs, which this machine's memory budget does not want.

    The game directory, the world and the UI test selection are handed over as
    **project properties**, not as game arguments: the checkout's own run
    configuration is what turns them into ``gameDirectory``/``--quickPlaySingleplayer``/
    ``-Djech.uitest.run`` for whichever loader the module uses. That is also why the
    property names live in the checkout's build files — see the ``gradle`` section
    of README.md.
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
    its mtime proves it was (re)written after the run started. Reads are
    incremental so a long run does not re-read its own log, and the whole capture
    is kept for the checks and for evidence next to the report.
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
