"""Evaluating the checks an env declares.

Six declared types — ``log_contains``, ``log_not_contains``, ``log_regex``,
``file_exists``, ``no_crash_reports``, ``exit_code_zero`` — plus three
``internal:`` checks the runner always adds. The internal ones exist so a run
whose *evidence* is missing (no game session, watchdog timeout) fails on a named
check instead of passing vacuously, which is the fail-closed rule the CloudLib
harness states for a missing report.

Statuses:

* ``PASS``  — evaluated and satisfied.
* ``FAIL``  — evaluated and not satisfied.
* ``SKIP``  — could not be evaluated (its precondition is reported by an
  ``internal:`` check). SKIP is never rendered as a pass.

A run is ``PASS`` only when no check FAILed.
"""

from __future__ import annotations

import re
from pathlib import Path

from .errors import ConfigError

INTERNAL_PREFIX = "internal:"

PASS = "PASS"
FAIL = "FAIL"
SKIP = "SKIP"


class CheckContext:
    """Everything the checks may look at, gathered by the runner."""

    def __init__(self, *, log_text="", log_path=None, minecraft_dir=None, session_started=False,
                 exit_code=None, exit_code_source="unknown", timed_out=False, new_crash_reports=(),
                 game_started=False, game_exited=False, duration_sec=None, timeout_sec=None):
        self.log_text = log_text or ""
        self.log_path = log_path
        # The instance's minecraft directory: the root every file_exists value is
        # relative to. None when no instance was located at all.
        self.minecraft_dir = Path(minecraft_dir) if minecraft_dir else None
        self.session_started = bool(session_started)
        self.exit_code = exit_code
        self.exit_code_source = exit_code_source
        self.timed_out = bool(timed_out)
        self.new_crash_reports = list(new_crash_reports or [])
        self.game_started = bool(game_started)
        self.game_exited = bool(game_exited)
        self.duration_sec = duration_sec
        self.timeout_sec = timeout_sec


def result(name, type_, status, detail):
    return {"name": name, "type": type_, "status": status, "detail": detail}


def evaluate(checks, ctx):
    """Evaluate the env's declared checks, in order."""
    return [evaluate_one(spec, ctx) for spec in checks]


def evaluate_one(spec, ctx):
    try:
        if spec.type == "log_contains":
            return _log_contains(spec, ctx)
        if spec.type == "log_not_contains":
            return _log_not_contains(spec, ctx)
        if spec.type == "log_regex":
            return _log_regex(spec, ctx)
        if spec.type == "file_exists":
            return _file_exists(spec, ctx)
        if spec.type == "no_crash_reports":
            return _no_crash_reports(spec, ctx)
        if spec.type == "exit_code_zero":
            return _exit_code_zero(spec, ctx)
    except re.error as exc:
        raise ConfigError(f"check {spec.name!r} has an invalid regex: {exc}")
    raise ConfigError(f"check {spec.name!r} has unknown type {spec.type!r}")


def internal_checks(ctx):
    """The precondition checks. They are part of every report."""
    started = result(
        INTERNAL_PREFIX + "session-started", "internal", PASS if ctx.session_started else FAIL,
        (f"a new game session wrote {_log_name(ctx)}" if ctx.session_started
         else f"no new {_log_name(ctx)} appeared before the startup grace expired"),
    )
    watchdog = result(
        INTERNAL_PREFIX + "watchdog", "internal", FAIL if ctx.timed_out else PASS,
        (f"run exceeded timeout_sec={ctx.timeout_sec}; the process tree was killed"
         if ctx.timed_out else
         f"finished within timeout_sec={ctx.timeout_sec}"
         + (f" ({ctx.duration_sec:.0f}s)" if ctx.duration_sec is not None else "")),
    )
    if not ctx.game_started:
        exited = result(INTERNAL_PREFIX + "game-exit", "internal", FAIL,
                        "the game process never appeared in the instance's process table")
    elif ctx.timed_out:
        exited = result(INTERNAL_PREFIX + "game-exit", "internal", FAIL,
                        "the game was still running when the watchdog killed it")
    elif ctx.game_exited:
        code = "unknown" if ctx.exit_code is None else str(ctx.exit_code)
        exited = result(INTERNAL_PREFIX + "game-exit", "internal", PASS,
                        f"the game process exited (exit code {code}, source={ctx.exit_code_source})")
    else:
        exited = result(INTERNAL_PREFIX + "game-exit", "internal", FAIL,
                        "the game process was still running when tracking stopped")
    return [started, watchdog, exited]


def overall_status(check_results):
    return FAIL if any(item["status"] == FAIL for item in check_results) else PASS


def summarize(check_results):
    total = len(check_results)
    failed = sum(1 for item in check_results if item["status"] == FAIL)
    skipped = sum(1 for item in check_results if item["status"] == SKIP)
    return {
        "checks": total,
        "passed": total - failed - skipped,
        "failed": failed,
        "skipped": skipped,
    }


# ---------------------------------------------------------------------------
# individual checks
# ---------------------------------------------------------------------------


def _values(spec):
    value = spec.value
    if isinstance(value, (list, tuple)):
        return [str(v) for v in value]
    if value is None:
        return []
    return [str(value)]


def _log_name(ctx):
    return str(ctx.log_path).rsplit("/", 1)[-1] if ctx.log_path else "latest.log"


def _log_contains(spec, ctx):
    wanted = _values(spec)
    if not ctx.session_started:
        return result(spec.name, spec.type, SKIP, "no game session log to inspect")
    missing = [value for value in wanted if value not in ctx.log_text]
    if missing:
        return result(spec.name, spec.type, FAIL,
                      "missing from the log: " + "; ".join(repr(m) for m in missing))
    return result(spec.name, spec.type, PASS, f"found {len(wanted)} pattern(s) in the log")


def _log_not_contains(spec, ctx):
    if not ctx.session_started:
        return result(spec.name, spec.type, SKIP, "no game session log to inspect")
    found = [value for value in _values(spec) if value in ctx.log_text]
    if found:
        return result(spec.name, spec.type, FAIL,
                      "must not appear but did: " + "; ".join(repr(f) for f in found))
    return result(spec.name, spec.type, PASS,
                  f"none of {len(_values(spec))} pattern(s) appear in the log")


def _log_regex(spec, ctx):
    if not ctx.session_started:
        return result(spec.name, spec.type, SKIP, "no game session log to inspect")
    missing = []
    for pattern in _values(spec):
        if re.search(pattern, ctx.log_text, re.MULTILINE) is None:
            missing.append(pattern)
    if missing:
        return result(spec.name, spec.type, FAIL,
                      "no match for: " + "; ".join(repr(m) for m in missing))
    return result(spec.name, spec.type, PASS, f"matched {len(_values(spec))} regex(es)")


def _file_exists(spec, ctx):
    """A path (or list of paths) that must exist under the instance's minecraft dir.

    This is the check for "the run produced this file" — a generated config, a
    written marker — where the log alone cannot prove it. The instance directory
    is the precondition: without one there is nothing to look in, and the check
    SKIPs instead of pretending.
    """
    wanted = _values(spec)
    if ctx.minecraft_dir is None:
        return result(spec.name, spec.type, SKIP, "no instance directory was located to inspect")
    missing = [value for value in wanted if not (ctx.minecraft_dir / value).exists()]
    if missing:
        return result(spec.name, spec.type, FAIL,
                      "missing under the instance's minecraft directory: "
                      + "; ".join(repr(m) for m in missing))
    return result(spec.name, spec.type, PASS,
                  f"found {len(wanted)} file(s) under the instance's minecraft directory")


def _no_crash_reports(spec, ctx):
    if ctx.new_crash_reports:
        return result(spec.name, spec.type, FAIL,
                      "crash report(s) written during the run: " + ", ".join(ctx.new_crash_reports))
    if not ctx.session_started:
        return result(spec.name, spec.type, SKIP,
                      "no game session; nothing could have written a crash report")
    return result(spec.name, spec.type, PASS, "no crash report was written during the run")


def _exit_code_zero(spec, ctx):
    if ctx.exit_code_source == "handoff":
        return result(spec.name, spec.type, SKIP,
                      "launcher handoff: the launcher handed off to a running Prism instance, so the "
                      "game's own exit code is not observable from the CLI")
    if ctx.exit_code is None:
        return result(spec.name, spec.type, FAIL, "the process exit code could not be determined")
    if ctx.exit_code == 0:
        return result(spec.name, spec.type, PASS,
                      f"exit code 0 (source={ctx.exit_code_source})")
    return result(spec.name, spec.type, FAIL,
                  f"exit code {ctx.exit_code} (source={ctx.exit_code_source})")
