"""Evaluating the checks an env declares.

Six declared types — ``log_contains``, ``log_not_contains``, ``log_regex``,
``file_exists``, ``no_crash_reports``, ``exit_code_zero`` — plus three
``internal:`` checks the runner always adds, plus ``uitest_report`` for an env
that runs the checkout's in-client UI test harness. The internal ones exist so a
run whose *evidence* is missing (no game session, watchdog timeout) fails on a
named check instead of passing vacuously, which is the fail-closed rule the
CloudLib harness states for a missing report.

``uitest_report`` is the same rule applied to the harness's own report: the
harness writes ``report.json`` itself (before it quits the client), so the file's
absence means the run never reached the runner, and a report that is there is
believed over the log. A missing, unreadable or unparseable report is a FAIL —
never a SKIP.

Statuses:

* ``PASS``  — evaluated and satisfied.
* ``FAIL``  — evaluated and not satisfied.
* ``SKIP``  — could not be evaluated (its precondition is reported by an
  ``internal:`` check). SKIP is never rendered as a pass.

A run is ``PASS`` only when no check FAILed.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

from .errors import ConfigError

INTERNAL_PREFIX = "internal:"

PASS = "PASS"
FAIL = "FAIL"
SKIP = "SKIP"

STOP_GOAL_REACHED = "goal-reached"
STOP_READY_TIMEOUT = "ready-timeout"
DELIBERATE_STOP_REASONS = (STOP_GOAL_REACHED, STOP_READY_TIMEOUT)

UITEST_REPORT_DIR = "uitest"
UITEST_REPORT_DEFAULT = f"{UITEST_REPORT_DIR}/report.json"
UITEST_CAPTURES_DIR = f"{UITEST_REPORT_DIR}/screenshots"
UITEST_REPORT_SCHEMA = 1
UITEST_PASS = "PASS"
UITEST_FAILURE_DETAIL_LIMIT = 3


class CheckContext:
    """Everything the checks may look at, gathered by the runner."""

    def __init__(self, *, log_text="", log_path=None, minecraft_dir=None, session_started=False,
                 exit_code=None, exit_code_source="unknown", timed_out=False, new_crash_reports=(),
                 game_started=False, game_exited=False, duration_sec=None, timeout_sec=None,
                 stop_reason=None, ready_reached=True):
        self.log_text = log_text or ""
        self.log_path = log_path
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
        self.stop_reason = stop_reason
        self.ready_reached = bool(ready_reached)


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
        if spec.type == "uitest_report":
            return _uitest_report(spec, ctx)
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
                        "the game process never appeared in the process table")
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
    """A path (or list of paths) that must exist under the run's game directory.

    This is the check for "the run produced this file" — a generated config, a
    written marker — where the log alone cannot prove it. That game directory
    (``runs/<id>/game``) is the precondition: without one there is nothing to look
    in, and the check SKIPs instead of pretending.
    """
    wanted = _values(spec)
    if ctx.minecraft_dir is None:
        return result(spec.name, spec.type, SKIP, "no game directory was located to inspect")
    missing = [value for value in wanted if not (ctx.minecraft_dir / value).exists()]
    if missing:
        return result(spec.name, spec.type, FAIL,
                      "missing under the run's game directory: "
                      + "; ".join(repr(m) for m in missing))
    return result(spec.name, spec.type, PASS,
                  f"found {len(wanted)} file(s) under the run's game directory")


def _no_crash_reports(spec, ctx):
    if ctx.new_crash_reports:
        return result(spec.name, spec.type, FAIL,
                      "crash report(s) written during the run: " + ", ".join(ctx.new_crash_reports))
    if not ctx.session_started:
        return result(spec.name, spec.type, SKIP,
                      "no game session; nothing could have written a crash report")
    return result(spec.name, spec.type, PASS, "no crash report was written during the run")


def _exit_code_zero(spec, ctx):
    """The game ended — was that a healthy end?

    A client launched by a checkout's run task quits only when something ends it,
    so three outcomes count as healthy: the runner ended the session on purpose
    once the env was ready (``stop_reason``), or the game itself exited 0 as part
    of a normal shutdown. A natural exit *before* the ready marker appeared, a
    non-zero exit, or an unobservable one all fail — the watchdog stays a separate
    exit code 3.
    """
    if ctx.stop_reason in DELIBERATE_STOP_REASONS:
        return result(spec.name, spec.type, PASS,
                      f"the runner ended the session on purpose (stop_reason={ctx.stop_reason})")
    if ctx.exit_code_source == "detached":
        return result(spec.name, spec.type, SKIP,
                      "detached launcher: the launcher exited while the game kept running, so the "
                      "game's own exit code is not observable from here")
    if ctx.exit_code is None:
        return result(spec.name, spec.type, FAIL, "the process exit code could not be determined")
    if not ctx.game_exited:
        return result(spec.name, spec.type, FAIL,
                      "the game was still running when tracking stopped")
    if not ctx.ready_reached:
        return result(spec.name, spec.type, FAIL,
                      f"the game exited before the ready marker appeared (exit code {ctx.exit_code})")
    if ctx.exit_code == 0:
        return result(spec.name, spec.type, PASS,
                      f"exit code 0 (source={ctx.exit_code_source})")
    return result(spec.name, spec.type, FAIL,
                  f"exit code {ctx.exit_code} (source={ctx.exit_code_source})")


def _uitest_report(spec, ctx):
    """The in-client UI test harness's own verdict, read from its report.

    ``value`` is the report path relative to the run's game directory;
    without one the harness's default output location is used. The check is a
    FAIL whenever no verdict can be read — a missing file, an unreadable one, a
    report this version does not know, a report without totals — because "the
    scenarios ran and passed" is exactly what it is here to establish, and an
    unevaluated verdict is not a pass.

    The captures the harness wrote are named by directory and count rather than
    copied: they are already in the run directory, which the run's own report and
    the caller's archive keep.
    """
    relative = spec.value or UITEST_REPORT_DEFAULT
    if ctx.minecraft_dir is None:
        return result(spec.name, spec.type, SKIP, "no game directory was located to inspect")
    path = ctx.minecraft_dir / relative
    if not path.is_file():
        return result(spec.name, spec.type, FAIL,
                      f"no UI test report at {relative}: the harness run never reached its runner "
                      "(a client that crashed, was killed, or never loaded the harness)")
    try:
        data = json.loads(path.read_text(encoding="utf-8", errors="replace"))
    except (OSError, ValueError) as exc:
        return result(spec.name, spec.type, FAIL,
                      f"{relative} could not be read as JSON: {exc.__class__.__name__}: {exc}")
    if not isinstance(data, dict):
        return result(spec.name, spec.type, FAIL,
                      f"{relative} holds a {type(data).__name__}, not the harness's report object")
    if data.get("schema") != UITEST_REPORT_SCHEMA:
        return result(spec.name, spec.type, FAIL,
                      f"{relative} is report schema {data.get('schema')!r}, and this check reads "
                      f"schema {UITEST_REPORT_SCHEMA}: the harness's report format changed and the "
                      "check has to be re-read against it")
    totals = data.get("totals")
    if not isinstance(totals, dict):
        return result(spec.name, spec.type, FAIL, f"{relative} carries no totals block")
    failed = totals.get("failed")
    errored = totals.get("errored")
    if any(isinstance(value, bool) or not isinstance(value, int) for value in (failed, errored)):
        return result(spec.name, spec.type, FAIL,
                      f"{relative} totals carry no integer failed/errored counters")
    summary = _uitest_summary(totals)
    status = data.get("status")
    if failed or errored:
        return result(spec.name, spec.type, FAIL,
                      f"the harness reported {status!r} with {errored} errored and {failed} failed "
                      f"scenario(s): {summary}; {_uitest_failures(data)}")
    if status != UITEST_PASS:
        return result(spec.name, spec.type, FAIL,
                      f"the harness reported {status!r}: {summary}; {_uitest_failures(data)}")
    return result(spec.name, spec.type, PASS, f"harness {status}: {summary}")


def _uitest_summary(totals):
    """The one-line tally the harness's own gradle verification prints."""
    checks = totals.get("checks")
    checks_failed = totals.get("checksFailed")
    passed_checks = (checks - checks_failed
                     if isinstance(checks, int) and isinstance(checks_failed, int) else "?")
    return (f"{totals.get('passed')}/{totals.get('scenarios')} scenarios, "
            f"{passed_checks}/{checks} checks, {totals.get('captures')} captures in "
            f"{UITEST_CAPTURES_DIR}/")


def _uitest_failures(data, limit=UITEST_FAILURE_DETAIL_LIMIT):
    """Name the scenarios that are not PASS, so the failure detail is actionable."""
    scenarios = data.get("scenarios")
    if not isinstance(scenarios, list):
        return "the report names no scenarios"
    bad = [item for item in scenarios
           if isinstance(item, dict) and item.get("status") != UITEST_PASS]
    if not bad:
        return "no scenario is non-PASS"
    named = []
    for item in bad[:limit]:
        message = item.get("message")
        named.append(f"{item.get('name')}={item.get('status')}"
                     + (f" ({message})" if message else ""))
    if len(bad) > limit:
        named.append(f"+{len(bad) - limit} more")
    return "non-PASS scenarios: " + ", ".join(named)
