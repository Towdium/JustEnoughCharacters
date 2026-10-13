"""Writing and verifying a run report.

Three artifacts per run directory, each written atomically (temp file +
``os.replace``) so a reader never sees a partial report:

* ``report.json`` — the machine-readable verdict, the one the gate reads;
* ``report.txt``  — the same verdict for a human reading a CI log;
* ``junit.xml``   — one testcase per check, for CI test reporters.

``jt verify`` is fail-closed: a missing, unreadable or structurally wrong
``report.json`` is a failure, never a pass. Exit codes: ``0`` pass, ``1`` a check
failed (or the report is unusable), ``3`` the watchdog fired.
"""

from __future__ import annotations

from pathlib import Path
from xml.sax.saxutils import escape, quoteattr

from . import checks as checks_mod
from . import paths
from .errors import EXIT_FAIL, EXIT_OK, EXIT_WATCHDOG, ReportError
from .util import atomic_write_text, dump_json_text, load_json

REPORT_JSON = "report.json"
REPORT_TXT = "report.txt"
JUNIT_XML = "junit.xml"
RUN_META = "run.json"
SCHEMA_VERSION = 1

EXIT_CODE_TABLE = {
    EXIT_OK: "0 = every check passed",
    EXIT_FAIL: "1 = a check failed, or the report is missing/corrupt (fail-closed)",
    EXIT_WATCHDOG: "3 = a run exceeded its watchdog budget",
}


def new_report(*, run_id, selection, started_at, status="FAIL", reason=""):
    """A report skeleton; the runner fills ``envs`` and calls :func:`finalize`."""
    return {
        "schema": SCHEMA_VERSION,
        "runId": run_id,
        "status": status,
        "reason": reason,
        "selection": {
            "expression": selection.expression,
            "exclude": selection.exclude_expression,
            "describe": selection.describe(),
            "empty": selection.is_empty,
            "matched": [],
            "total": 0,
            "explanation": "",
        },
        "startedAt": started_at,
        "finishedAt": None,
        "durationMs": 0,
        "envs": [],
        "totals": {"envs": 0, "passed": 0, "failed": 0, "checks": 0, "checksFailed": 0,
                   "checksSkipped": 0, "watchdogs": 0},
    }


def finalize(report, *, finished_at, duration_ms):
    """Recompute the totals and the top-level status from the env entries."""
    report["finishedAt"] = finished_at
    report["durationMs"] = int(duration_ms)
    envs = report.get("envs") or []
    checks_total = checks_failed = checks_skipped = watchdogs = 0
    for entry in envs:
        for item in entry.get("checks") or []:
            checks_total += 1
            if item.get("status") == checks_mod.FAIL:
                checks_failed += 1
            elif item.get("status") == checks_mod.SKIP:
                checks_skipped += 1
        if entry.get("watchdog"):
            watchdogs += 1
    failed_envs = [e for e in envs if e.get("status") != checks_mod.PASS]
    report["totals"] = {
        "envs": len(envs),
        "passed": len(envs) - len(failed_envs),
        "failed": len(failed_envs),
        "checks": checks_total,
        "checksFailed": checks_failed,
        "checksSkipped": checks_skipped,
        "watchdogs": watchdogs,
    }
    if not envs:
        report["status"] = checks_mod.FAIL
        if not report.get("reason"):
            report["reason"] = "no env was executed, so nothing was verified"
    else:
        report["status"] = checks_mod.FAIL if failed_envs else checks_mod.PASS
        if not failed_envs:
            report["reason"] = report.get("reason") or "every env passed every check"
    return report


# ---------------------------------------------------------------------------
# writing
# ---------------------------------------------------------------------------


def write_report(run_dir, report, *, log=None):
    """Write report.json, report.txt and junit.xml atomically; return their paths."""
    run_dir = Path(run_dir)
    run_dir.mkdir(parents=True, exist_ok=True)
    json_path = run_dir / REPORT_JSON
    txt_path = run_dir / REPORT_TXT
    xml_path = run_dir / JUNIT_XML
    # json first: the gate reads it, and a crash between writes leaves the newest
    # verdict on disk rather than a text file that disagrees with it.
    atomic_write_text(json_path, dump_json_text(report))
    atomic_write_text(txt_path, render_text(report))
    atomic_write_text(xml_path, render_junit(report))
    if log:
        log(f"report: {paths.display(json_path)}")
        log(f"report: {paths.display(txt_path)}")
        log(f"report: {paths.display(xml_path)}")
    return {"json": json_path, "txt": txt_path, "junit": xml_path}


def render_text(report):
    envs = report.get("envs") or []
    totals = report.get("totals") or {}
    selection = report.get("selection") or {}
    lines = [
        f"jt run {report.get('runId', '?')} — {report.get('status', '?')}",
        f"selection: {selection.get('describe', '?')}",
        f"           {selection.get('explanation', '')}",
        f"duration:  {report.get('durationMs', 0)} ms",
        f"totals:    {totals.get('passed', 0)}/{totals.get('envs', 0)} env passed, "
        f"{totals.get('checks', 0) - totals.get('checksFailed', 0) - totals.get('checksSkipped', 0)}"
        f"/{totals.get('checks', 0)} checks passed, {totals.get('checksSkipped', 0)} skipped, "
        f"{totals.get('watchdogs', 0)} watchdog",
    ]
    if report.get("reason"):
        lines.append(f"reason:    {report['reason']}")
    for entry in envs:
        lines.append("")
        lines.append(
            f"[{entry.get('status', '?')}] {entry.get('name', '?')} "
            f"({entry.get('mc', '?')}/{((entry.get('loader') or {}).get('type', '?'))} "
            f"java {entry.get('java', '?')})"
        )
        lines.append(
            f"    instance={entry.get('instanceId') or '(none)'} "
            f"reused={bool(entry.get('instanceReused'))} packHash={str(entry.get('packHash'))[:16]} "
            f"exitCode={entry.get('exitCode')} ({entry.get('exitCodeSource')}) "
            f"timedOut={bool(entry.get('timedOut'))} stopReason={entry.get('stopReason') or '-'}"
        )
        if entry.get("readyWhen"):
            lines.append(
                f"    ready_when={entry.get('readyWhen')!r} "
                f"readyReached={bool(entry.get('readyReached'))}"
            )
        for mod in entry.get("mods") or []:
            pin = mod.get("pin") or {}
            pinned = pin.get("fileId") or pin.get("versionId") or "local"
            lines.append(f"    mod {mod.get('name')}: {mod.get('filename')} [{pinned}]")
        for item in entry.get("checks") or []:
            lines.append(f"    {item.get('status'):4} {item.get('name')}: {item.get('detail')}")
        for note in entry.get("notes") or []:
            lines.append(f"    note: {note}")
        if entry.get("log"):
            lines.append(f"    log: {entry['log']}")
    lines.append("")
    lines.append("exit codes: " + "; ".join(EXIT_CODE_TABLE.values()))
    return "\n".join(lines) + "\n"


def render_junit(report):
    envs = report.get("envs") or []
    totals = report.get("totals") or {}
    tests = totals.get("checks", 0)
    failures = totals.get("checksFailed", 0)
    skipped = totals.get("checksSkipped", 0)
    lines = ['<?xml version="1.0" encoding="UTF-8"?>']
    lines.append(
        f'<testsuites name="jech-compat" tests="{tests}" failures="{failures}" '
        f'skipped="{skipped}" time="{report.get("durationMs", 0) / 1000.0:.3f}">'
    )
    for entry in envs:
        entry_checks = entry.get("checks") or []
        entry_failures = sum(1 for c in entry_checks if c.get("status") == checks_mod.FAIL)
        entry_skipped = sum(1 for c in entry_checks if c.get("status") == checks_mod.SKIP)
        lines.append(
            f'  <testsuite name={quoteattr(str(entry.get("name", "?")))} '
            f'tests="{len(entry_checks)}" failures="{entry_failures}" skipped="{entry_skipped}" '
            f'time="{(entry.get("durationMs") or 0) / 1000.0:.3f}">'
        )
        lines.append("    <properties>")
        for key in ("mc", "instanceId", "packHash", "log"):
            value = entry.get(key)
            if value:
                lines.append(f'      <property name={quoteattr(str(key))} value={quoteattr(str(value))} />')
        loader = entry.get("loader") or {}
        if loader.get("type"):
            loader_label = f"{loader.get('type')}-{loader.get('version')}"
            lines.append(f'      <property name="loader" value={quoteattr(loader_label)} />')
        lines.append("    </properties>")
        for item in entry_checks:
            status = item.get("status")
            lines.append(
                f'    <testcase classname={quoteattr(str(entry.get("name", "?")))} '
                f'name={quoteattr(str(item.get("name", "?")))} '
                f'time="{(item.get("durationMs") or 0) / 1000.0:.3f}">'
            )
            detail = str(item.get("detail") or "")
            if status == checks_mod.FAIL:
                lines.append(f'      <failure message={quoteattr(detail[:200])}>{escape(detail)}</failure>')
            elif status == checks_mod.SKIP:
                lines.append(f'      <skipped message={quoteattr(detail[:200])} />')
            lines.append("    </testcase>")
        lines.append("  </testsuite>")
    lines.append("</testsuites>")
    return "\n".join(lines) + "\n"


# ---------------------------------------------------------------------------
# reading and verifying
# ---------------------------------------------------------------------------


def resolve_report_dir(target):
    target = Path(target)
    if target.is_dir():
        return target
    if target.is_file() and target.name == REPORT_JSON:
        return target.parent
    raise ReportError(
        f"{paths.display(target)}: not a run directory and not a report.json (fail-closed)"
    )


def load_report(target):
    """Read a report, refusing anything that is not a structurally valid verdict."""
    run_dir = resolve_report_dir(target)
    path = run_dir / REPORT_JSON
    if not path.is_file():
        raise ReportError(
            f"no {REPORT_JSON} in {paths.display(run_dir)} — the run never reached the reporter, "
            "matched nothing, or the directory is not a run (fail-closed)"
        )
    try:
        report = load_json(path, what=f"{REPORT_JSON} in {paths.display(run_dir)}")
    except Exception as exc:  # noqa: BLE001 - any read/parse failure is a failed gate
        raise ReportError(f"{paths.display(path)}: unreadable report ({exc})")
    problems = validate_report(report)
    if problems:
        raise ReportError(
            f"{paths.display(path)}: report is not usable:\n  - " + "\n  - ".join(problems)
        )
    return report


def validate_report(report):
    problems = []
    if not isinstance(report, dict):
        return [f"report must be a json object, got {type(report).__name__}"]
    for key in ("schema", "runId", "status", "envs", "totals", "selection"):
        if key not in report:
            problems.append(f"missing key {key!r}")
    if report.get("status") not in (checks_mod.PASS, checks_mod.FAIL):
        problems.append(f"status {report.get('status')!r} is neither PASS nor FAIL")
    if not isinstance(report.get("envs"), list):
        problems.append("envs must be a list")
    if not isinstance(report.get("totals"), dict):
        problems.append("totals must be an object")
    return problems


def exit_code_for(report):
    """0 pass, 3 watchdog, 1 anything else — the CloudLib harness's convention."""
    if report.get("status") == checks_mod.PASS and not (report.get("totals") or {}).get("watchdogs"):
        if not (report.get("envs") or []):
            return EXIT_FAIL  # a report with nothing in it is not a pass
        return EXIT_OK
    if (report.get("totals") or {}).get("watchdogs"):
        return EXIT_WATCHDOG
    return EXIT_FAIL


def verify_run(target, *, log=print):
    """``jt verify``: print the verdict and return the exit code (fail-closed)."""
    run_dir = resolve_report_dir(target)
    report = load_report(run_dir)
    totals = report.get("totals") or {}
    log(f"run {report.get('runId')}: {report.get('status')} — "
        f"{totals.get('passed', 0)}/{totals.get('envs', 0)} env passed, "
        f"{totals.get('checksFailed', 0)} failed check(s), "
        f"{totals.get('checksSkipped', 0)} skipped, "
        f"{totals.get('watchdogs', 0)} watchdog")
    explanation = (report.get("selection") or {}).get("explanation")
    if explanation:
        log(explanation)
    if report.get("reason"):
        log(f"reason: {report['reason']}")
    for entry in report.get("envs") or []:
        if entry.get("status") == checks_mod.PASS:
            continue
        log(f"FAIL {entry.get('name')}: exitCode={entry.get('exitCode')} "
            f"timedOut={bool(entry.get('timedOut'))}")
        for item in entry.get("checks") or []:
            if item.get("status") != checks_mod.PASS:
                log(f"  {item.get('status')} {item.get('name')}: {item.get('detail')}")
        if entry.get("log"):
            log(f"  log: {entry['log']}")
    return exit_code_for(report)
