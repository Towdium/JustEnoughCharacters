"""``jt run``: build, isolate, launch, watch, judge.

One run per env, each in its own ``runs/<id>`` directory, executed strictly
serially — a 16 GB machine cannot host two Minecraft clients with modded
modpacks at once, so the harness never parallelises runs.

The sequence per env, all of it fail-closed:

1. resolve (auto-pin anything still unresolved) and download into the cache;
2. render the ``.mrpack`` into ``runs/<id>/pack`` (files list empty, all jars in
   ``overrides/mods``);
3. reuse the env's Prism instance when its modset hash is unchanged, else import;
4. isolate: re-sync ``mods/``, archive then clear ``config/``, clear ``saves/`` and
   ``logs/``;
5. launch with ``prismlauncher -d <root> --launch <id>`` in the background, then
   follow ``<instance>/minecraft/logs/latest.log`` — only accepting a file that is
   newer than the run start, since ``latest.log`` can still be the previous
   session's;
6. a watchdog on ``timeout_sec`` kills the process tree and yields exit code 3;
7. evaluate the env's checks plus the always-present ``internal:`` checks, and
   write report.json / report.txt / junit.xml.

Exit codes: 0 pass, 1 a check failed, 2 a configuration or infrastructure error,
3 watchdog. A multi-env selection reports the highest severity it saw.
"""

from __future__ import annotations

import time
from pathlib import Path

from . import checks as checks_mod
from . import envs as envs_mod
from . import instance, pack as pack_mod, paths, report as report_mod, resolve
from .errors import EXIT_FAIL, EXIT_OK, EXIT_USAGE, EXIT_WATCHDOG, JtError
from .select import Selection
from .util import atomic_write_json, atomic_write_text, now_iso, now_stamp, sanitize_name

POLL_SEC = 0.5
STARTUP_GRACE_SEC = 180
LAUNCHER_EXIT_GRACE_SEC = 60
# How long the run keeps waiting, after the ``ready_when`` line has appeared,
# for the env's ``log_contains`` markers to show up too. In a healthy run they
# are already there when the ready line lands (the markers are earlier), so the
# runner stops at once; the grace only bounds a run whose markers never arrive,
# turning it into an assertion FAIL instead of a watchdog kill.
READY_GRACE_SEC = 120
CONFIG_ERROR = "configError"


class RunOutcome:
    def __init__(self, report, report_dir, results):
        self.report = report
        self.report_dir = report_dir
        self.results = results

    @property
    def exit_code(self):
        codes = [code for _entry, code in self.results]
        return max(codes) if codes else EXIT_FAIL


# ---------------------------------------------------------------------------
# entry points
# ---------------------------------------------------------------------------


def run_selection(expression, exclude=None, *, envs=None, refresh=False, log=print):
    """Run every env the selection matches; returns the process exit code."""
    paths.ensure_data_dirs()
    selection = Selection.parse(expression, exclude)
    all_envs = list(envs) if envs is not None else envs_mod.load_all()
    matched = selection.select(all_envs, name_of=lambda env: env.name, tags_of=lambda env: env.tags)

    started_at = now_iso()
    started = time.monotonic()
    run_id = f"{sanitize_name(selection.expression or 'selection')}-{now_stamp()}"
    agg_dir = paths.runs_dir() / run_id
    report = report_mod.new_report(run_id=run_id, selection=selection, started_at=started_at)
    report["selection"].update({
        "matched": [env.name for env in matched],
        "total": len(all_envs),
        "explanation": selection.explain([env.name for env in matched], len(all_envs)),
    })
    log(report["selection"]["explanation"])

    results = []
    if not matched:
        report["reason"] = "the selection matched no env, so nothing was executed"
        report_mod.finalize(report, finished_at=now_iso(), duration_ms=0)
        report_mod.write_report(agg_dir, report, log=log)
        return EXIT_FAIL

    for env in matched:
        entry, code = run_env(env, refresh=refresh, log=log)
        report["envs"].append(entry)
        results.append((entry, code))

    report_mod.finalize(report, finished_at=now_iso(),
                        duration_ms=(time.monotonic() - started) * 1000)
    report_mod.write_report(agg_dir, report, log=log)
    log(f"selection run: {paths.display(agg_dir)} — {report['status']}")
    return RunOutcome(report, agg_dir, results).exit_code


def run_env(env, *, refresh=False, log=print, selection_label=None):
    """Run one env end to end; returns ``(report entry, exit code)``."""
    paths.ensure_data_dirs()
    run_id = f"{sanitize_name(env.name)}-{now_stamp()}"
    run_dir = paths.runs_dir() / run_id
    run_dir.mkdir(parents=True, exist_ok=True)
    started_iso = now_iso()
    started_wall = time.time()
    started = time.monotonic()

    entry = {
        "name": env.name,
        "envFile": paths.display(env.path),
        "runDir": paths.display(run_dir),
        "mc": env.mc,
        "loader": {"type": env.loader.type, "version": env.loader.version},
        "java": env.java_version,
        "startedAt": started_iso,
        "finishedAt": None,
        "durationMs": 0,
        "status": checks_mod.FAIL,
        "exitCodeKind": "config",
        "exitCode": None,
        "exitCodeSource": "unknown",
        "timedOut": False,
        "watchdog": False,
        "stopReason": None,
        "readyReached": None,
        "readyWhen": env.ready_when.value if env.ready_when is not None else None,
        "instanceId": None,
        "instanceReused": False,
        "instanceImported": False,
        "packHash": None,
        "dependencies": {},
        "mods": [],
        "isolation": None,
        "checks": [],
        "notes": [],
        "log": None,
        "launcherLog": None,
        "mrpack": None,
    }

    try:
        _execute(env, run_dir, entry, started_wall=started_wall, refresh=refresh, log=log)
    except JtError as exc:
        entry[CONFIG_ERROR] = True
        entry["notes"].append(f"{type(exc).__name__}: {exc}")
        entry["checks"] = [
            checks_mod.result("internal:run", "internal", checks_mod.FAIL, str(exc)),
        ]
        entry["status"] = checks_mod.FAIL
        entry["exitCodeKind"] = "config"
        log(f"{env.name}: {exc}")
    except OSError as exc:
        entry[CONFIG_ERROR] = True
        entry["notes"].append(f"OSError: {exc}")
        entry["checks"] = [
            checks_mod.result("internal:run", "internal", checks_mod.FAIL, f"OSError: {exc}"),
        ]
        entry["status"] = checks_mod.FAIL
        entry["exitCodeKind"] = "config"
        log(f"{env.name}: OSError: {exc}")

    duration_ms = (time.monotonic() - started) * 1000
    entry["finishedAt"] = now_iso()
    entry["durationMs"] = int(duration_ms)
    if entry.get("watchdog") or entry.get("timedOut"):
        entry["exitCodeKind"] = "watchdog"
    elif entry["status"] == checks_mod.PASS:
        entry["exitCodeKind"] = "pass"
    elif not entry.get(CONFIG_ERROR):
        entry["exitCodeKind"] = "check"

    exit_code = env_exit_code(entry)
    report = _env_report(run_id, env, entry, started_iso, duration_ms, selection_label=selection_label)
    report_mod.write_report(run_dir, report, log=log)
    atomic_write_json(run_dir / report_mod.RUN_META, entry)
    log(f"[{entry['status']}] {env.name} in {duration_ms / 1000.0:.1f}s "
        f"(exit code {exit_code}, {paths.display(run_dir)})")
    return entry, exit_code


def env_exit_code(entry):
    """The per-env exit code: 0 pass, 1 failed check, 2 config/infra error, 3 watchdog."""
    kind = entry.get("exitCodeKind")
    if kind == "watchdog" or entry.get("watchdog") or entry.get("timedOut"):
        return EXIT_WATCHDOG
    if kind == "config" or (entry.get(CONFIG_ERROR) and entry.get("status") != checks_mod.PASS):
        return EXIT_USAGE
    if entry.get("status") == checks_mod.PASS:
        return EXIT_OK
    return EXIT_FAIL


def _env_report(run_id, env, entry, started_iso, duration_ms, *, selection_label=None):
    selection = Selection.parse(selection_label or env.name)
    report = report_mod.new_report(run_id=run_id, selection=selection, started_at=started_iso)
    report["selection"].update({
        "matched": [env.name],
        "total": 1,
        "explanation": selection_label or f"single-env run of {env.name}",
    })
    report["envs"] = [entry]
    return report_mod.finalize(report, finished_at=entry["finishedAt"], duration_ms=duration_ms)


# ---------------------------------------------------------------------------
# the per-env body
# ---------------------------------------------------------------------------


def _execute(env, run_dir, entry, *, started_wall, refresh, log):
    resolved = resolve.resolve_env(env, refresh=refresh, download=True, log=log)
    entry["mods"] = [artifact.to_json() for artifact in resolved.artifacts]
    for note in resolved.notes:
        entry["notes"].append(note)

    # The same (env, modset) always maps to the same instance id; naming the pack
    # after it makes the import deterministic, so a retry reuses one instance
    # instead of accumulating suffixed ones.
    digest = instance.modset_hash(env, resolved)
    instance_label = instance.instance_id_for(env, digest)
    pack_result = pack_mod.build_pack(env, resolved, run_dir, version_id=instance_label, log=log)
    entry["mrpack"] = paths.display(pack_result.mrpack_path)
    entry["packHash"] = pack_result.pack_hash
    entry["dependencies"] = pack_result.dependencies
    log(f"{env.name}: pack {paths.display(pack_result.mrpack_path)} "
        f"({len(pack_result.mod_filenames)} jar(s) in overrides/mods, files list empty, "
        f"instance {instance_label})")

    info = instance.ensure_instance(env, resolved, pack_result, refresh=refresh, log=log)
    entry["instanceId"] = info.instance_id
    entry["instanceReused"] = info.reused
    entry["instanceImported"] = info.imported

    isolation = instance.isolate(info.instance_id, resolved, run_dir, log=log)
    entry["isolation"] = isolation.to_json()
    entry["notes"].extend(isolation.notes)

    minecraft_dir = instance.game_dir(info.instance_id)
    log_path = instance.latest_log_path(info.instance_id)
    launcher_log = run_dir / "launcher.log"
    entry["log"] = paths.display(log_path)
    entry["launcherLog"] = paths.display(launcher_log)

    ready_values, markers = readiness_rule(env)
    crash_before = crash_report_snapshot(minecraft_dir)
    proc = instance.launch(info.instance_id, log_path=launcher_log, log=log)
    follower = instance.LogFollower(log_path, started_wall, poll=POLL_SEC)
    watch = _watch(env, info.instance_id, proc, follower, started_wall, log=log,
                   ready_values=ready_values, markers=markers)

    entry["timedOut"] = watch["timed_out"]
    entry["watchdog"] = watch["timed_out"]
    entry["exitCode"] = watch["exit_code"]
    entry["exitCodeSource"] = watch["exit_code_source"]
    entry["stopReason"] = watch["stop_reason"]
    entry["readyReached"] = watch["ready_reached"]
    if watch["timed_out"]:
        entry["notes"].append(
            f"watchdog: the run exceeded timeout_sec={env.timeout_sec}; the process tree was killed"
        )
    elif watch["stop_reason"] in checks_mod.DELIBERATE_STOP_REASONS:
        detail = f"ready_when={env.ready_when.value!r}" if env.ready_when is not None else "no ready_when"
        entry["notes"].append(
            f"stop: the runner ended the session on purpose "
            f"(stop_reason={watch['stop_reason']}, {detail})"
        )

    if follower.text:
        atomic_write_text(run_dir / "game.log", follower.text)

    crash_after = crash_report_snapshot(minecraft_dir)
    new_crashes = sorted(crash_after - crash_before)
    if new_crashes:
        log(f"{env.name}: crash report(s) during the run: {', '.join(new_crashes)}")

    ctx = checks_mod.CheckContext(
        log_text=follower.text,
        log_path=paths.display(log_path),
        minecraft_dir=minecraft_dir,
        session_started=follower.session_started,
        exit_code=watch["exit_code"],
        exit_code_source=watch["exit_code_source"],
        timed_out=watch["timed_out"],
        new_crash_reports=new_crashes,
        game_started=watch["game_started"],
        game_exited=watch["game_exited"],
        duration_sec=watch["duration_sec"],
        timeout_sec=env.timeout_sec,
        stop_reason=watch["stop_reason"],
        ready_reached=watch["ready_reached"],
    )
    entry["checks"] = checks_mod.internal_checks(ctx) + checks_mod.evaluate(env.checks, ctx)
    entry["status"] = checks_mod.overall_status(entry["checks"])
    for item in entry["checks"]:
        if item["status"] != checks_mod.PASS:
            log(f"{env.name}: {item['status']} {item['name']}: {item['detail']}")
    if not follower.session_started:
        tail = tail_of(launcher_log)
        if tail:
            entry["notes"].append("launcher.log tail:\n" + tail)
            log(f"{env.name}: launcher.log tail:\n{tail}")


def readiness_rule(env):
    """``(ready values, required markers)`` for one env's stopping rule.

    ``ready values`` is the ``ready_when`` line (empty when the env declares
    none, in which case the runner never stops the game early). ``markers`` is
    every ``log_contains`` value: the env's own assertion of "JECh loaded", which
    must already hold before the runner considers the game ready.
    """
    ready = [env.ready_when.value] if env.ready_when is not None else []
    markers = [value for spec in env.checks if spec.type == "log_contains"
               for value in _check_values(spec)]
    return ready, markers


def _check_values(spec):
    value = spec.value
    if isinstance(value, (list, tuple)):
        return [str(v) for v in value]
    if value is None:
        return []
    return [str(value)]


def ready_line_seen(log_text, ready_values):
    """True once the env's ``ready_when`` line(s) are in the log (False with none)."""
    return bool(ready_values) and all(value in log_text for value in ready_values)


def all_markers_seen(log_text, markers):
    """True when every required ``log_contains`` marker is in the log."""
    return all(value in log_text for value in markers)


def _watch(env, instance_id, proc, follower, started_wall, *, log, ready_values=(), markers=()):
    """Track the game until it is ready, it exits, the launcher gives up, or the watchdog fires.

    When the env declares ``ready_when`` the run gets a stopping rule: the moment
    the ready line and every ``log_contains`` marker are in the log, the runner
    ends the instance's process tree itself (``stop_reason=goal-reached``) and
    moves on to judging. If the ready line lands but a marker never arrives, the
    run is bounded by ``READY_GRACE_SEC`` and then stopped too
    (``stop_reason=ready-timeout``), so a broken assertion surfaces as a FAIL
    instead of an unbounded hang.
    """
    deadline = started_wall + env.timeout_sec
    session_started = False
    game_started = False
    timed_out = False
    launcher_died_early = False
    stop_reason = None
    ready_seen_at = None
    pids = []

    while True:
        now = time.time()
        if session_started:
            follower.poll_once()
        else:
            session_started = follower.wait_for_session(min(now + POLL_SEC, deadline), log=log)
        pids = instance.game_pids(instance_id, exclude=(proc.pid,))
        if pids:
            game_started = True
            if proc.poll() is not None:
                # the CLI (or a Prism instance it handed over to) is not the
                # game's parent: the game's own exit code is unobservable then
                launcher_died_early = True
        if session_started and ready_values:
            if ready_seen_at is None and ready_line_seen(follower.text, ready_values):
                ready_seen_at = now
            if ready_seen_at is not None:
                if all_markers_seen(follower.text, markers):
                    stop_reason = checks_mod.STOP_GOAL_REACHED
                    break
                if now - ready_seen_at >= READY_GRACE_SEC:
                    stop_reason = checks_mod.STOP_READY_TIMEOUT
                    break
        if game_started and not pids:
            break
        if not game_started and proc.poll() is not None:
            break
        if time.time() >= deadline:
            timed_out = True
            break
        time.sleep(POLL_SEC)

    follower.poll_once()
    was_running = bool(pids)
    if stop_reason or timed_out or was_running:
        instance.kill_process_tree(sorted(set(pids) | {proc.pid}), log=log)
    exit_code = _reap(proc, log=log, timeout=LAUNCHER_EXIT_GRACE_SEC)
    if stop_reason:
        source = "runner"
    elif launcher_died_early:
        source = "handoff"
    elif exit_code is not None:
        source = "launcher"
    else:
        source = "unknown"
    if not session_started and not timed_out and time.time() < deadline:
        log(f"{env.name}: the launcher exited before a game session started")
    if stop_reason:
        log(f"{env.name}: the runner ended the session (stop_reason={stop_reason}, "
            f"after {time.time() - started_wall:.0f}s)")
    # A run the runner ended is exited by definition: SIGKILLed children can linger
    # in the process table for a moment, which must not read as "still running".
    game_exited = bool(stop_reason) or (
        game_started and not instance.game_pids(instance_id, exclude=(proc.pid,)))
    ready_reached = True if not ready_values else (
        ready_line_seen(follower.text, ready_values) and all_markers_seen(follower.text, markers))
    return {
        "timed_out": timed_out,
        "stop_reason": stop_reason,
        "ready_reached": ready_reached,
        "game_started": game_started,
        "game_exited": game_exited,
        "exit_code": exit_code,
        "exit_code_source": source,
        "duration_sec": time.time() - started_wall,
    }


def _reap(proc, *, log, timeout):
    if proc.poll() is not None:
        return proc.returncode
    try:
        return proc.wait(timeout=timeout)
    except Exception:
        log("launcher process did not exit within the grace period; leaving it alone")
        return None


def crash_report_snapshot(minecraft_dir):
    directory = Path(minecraft_dir) / "crash-reports"
    if not directory.is_dir():
        return set()
    return {entry.name for entry in directory.iterdir() if entry.is_file()}


def tail_of(path, lines=20):
    try:
        text = Path(path).read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""
    return "\n".join(text.strip().splitlines()[-lines:])
