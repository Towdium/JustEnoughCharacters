"""Report rendering, atomic writes, and the fail-closed verification gate."""

from __future__ import annotations

import contextlib
import io
import json
import tempfile
import unittest
import xml.etree.ElementTree as ElementTree
from pathlib import Path

from lib import checks, report
from lib.__main__ import main
from lib.errors import ReportError
from lib.select import Selection

from . import support


def entry(**overrides):
    data = {
        "name": "unit-env",
        "mc": "1.21.1",
        "loader": {"type": "neoforge", "version": "21.1.215"},
        "java": 21,
        "instanceId": "unit-instance",
        "instanceReused": False,
        "packHash": "abc123",
        "exitCode": 0,
        "exitCodeSource": "launcher",
        "timedOut": False,
        "watchdog": False,
        "durationMs": 1234,
        "status": checks.PASS,
        "log": "runs/unit/game.log",
        "mods": [{"name": "jei", "filename": "jei.jar", "pin": {"fileId": 1}}],
        "checks": [checks.result("jech-loaded", "log_contains", checks.PASS, "found"),
                   checks.result("no-crash", "no_crash_reports", checks.PASS, "none")],
        "notes": [],
    }
    data.update(overrides)
    return data


def skip_entry(**overrides):
    """An env the runner did not execute because the env file declared a skip."""
    data = {"name": "skipped-env", "status": checks.SKIP, "exitCodeKind": "skip",
            "skipReason": "needs a world load, and quick play needs 1.20+",
            "checks": [checks.result("internal:skip", "internal", checks.SKIP,
                                     "declared skip: needs a world load")],
            "world": None}
    data.update(overrides)
    return entry(**data)


def make_report(*entries, expression="all"):
    built = report.new_report(run_id="unit-run", selection=Selection.parse(expression),
                              started_at="2026-01-01T00:00:00+0000")
    built["selection"]["matched"] = [e["name"] for e in entries]
    built["selection"]["total"] = len(entries)
    built["selection"]["explanation"] = Selection.parse(expression).explain([e["name"] for e in entries], len(entries))
    built["envs"] = list(entries)
    return report.finalize(built, finished_at="2026-01-01T00:10:00+0000", duration_ms=600000)


class RollUpTest(unittest.TestCase):
    def test_a_passing_report_is_exit_code_zero(self):
        built = make_report(entry())
        self.assertEqual(built["status"], checks.PASS)
        self.assertEqual(report.exit_code_for(built), 0)
        self.assertEqual(built["totals"], {"envs": 1, "passed": 1, "failed": 0, "skipped": 0,
                                           "checks": 2, "checksFailed": 0, "checksSkipped": 0,
                                           "watchdogs": 0})

    def test_a_skipped_env_is_not_a_pass_and_keeps_the_selection_red(self):
        skipped = skip_entry()
        built = make_report(entry(name="real-env"), skipped)
        self.assertEqual(built["status"], checks.FAIL)
        self.assertEqual(report.exit_code_for(built), 1)
        self.assertEqual(built["totals"]["passed"], 1)
        self.assertEqual(built["totals"]["skipped"], 1)
        self.assertEqual(built["totals"]["failed"], 0, "a skip is not a failed check")
        self.assertIn("declared skip", built["reason"])
        self.assertIn(skipped["name"], built["reason"])

    def test_a_lone_skipped_env_is_its_own_reason(self):
        built = make_report(skip_entry())
        self.assertEqual(built["status"], checks.FAIL)
        self.assertEqual(built["totals"]["passed"], 0)
        self.assertIn("never a pass", built["reason"])
        self.assertIn("skip", report.render_text(built))
        self.assertIn("were NOT executed", report.render_text(built))

    def test_a_failed_check_is_exit_code_one(self):
        failed = entry(status=checks.FAIL, exitCode=1,
                       checks=[checks.result("jech-loaded", "log_contains", checks.FAIL, "missing")])
        built = make_report(failed)
        self.assertEqual(built["status"], checks.FAIL)
        self.assertEqual(report.exit_code_for(built), 1)
        self.assertEqual(built["totals"]["checksFailed"], 1)

    def test_a_watchdog_is_exit_code_three(self):
        built = make_report(entry(status=checks.FAIL, watchdog=True, timedOut=True, exitCode=None,
                                  checks=[checks.result("internal:watchdog", "internal",
                                                        checks.FAIL, "killed")]))
        self.assertEqual(report.exit_code_for(built), 3)
        self.assertEqual(built["totals"]["watchdogs"], 1)

    def test_a_report_with_no_env_is_never_a_pass(self):
        built = make_report()
        self.assertEqual(built["status"], checks.FAIL)
        self.assertEqual(report.exit_code_for(built), 1)
        self.assertIn("no env was executed", built["reason"])


class WriteTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.dir = Path(self._tmp.name) / "runs" / "unit-run"

    def test_all_three_artifacts_are_written(self):
        report.write_report(self.dir, make_report(entry()))
        for name in (report.REPORT_JSON, report.REPORT_TXT, report.JUNIT_XML):
            self.assertTrue((self.dir / name).is_file(), name)
        json.loads((self.dir / report.REPORT_JSON).read_text(encoding="utf-8"))

    def test_the_write_is_atomic_and_leaves_no_temp_files(self):
        report.write_report(self.dir, make_report(entry()))
        strays = [p.name for p in self.dir.iterdir() if ".tmp" in p.name or p.name.endswith(".part")]
        self.assertEqual(strays, [])

    def test_junit_is_wellformed_and_marks_failures_and_skips(self):
        failed = entry(name="failing-env", status=checks.FAIL, exitCode=1,
                       checks=[checks.result("jech-loaded", "log_contains", checks.FAIL,
                                             'missing "<marker>" & more'),
                               checks.result("no-crash", "no_crash_reports", checks.SKIP, "no session")])
        report.write_report(self.dir, make_report(entry(name="passing-env"), failed))
        text = (self.dir / report.JUNIT_XML).read_text(encoding="utf-8")
        root = ElementTree.fromstring(text)
        self.assertEqual(root.tag, "testsuites")
        self.assertEqual(root.attrib["tests"], "4")
        self.assertEqual(root.attrib["failures"], "1")
        self.assertEqual(root.attrib["skipped"], "1")
        suites = root.findall("testsuite")
        self.assertEqual([s.attrib["name"] for s in suites], ["passing-env", "failing-env"])
        failure = suites[1].find("testcase/failure")
        self.assertIsNotNone(failure)
        self.assertIn('missing "<marker>" & more', failure.text)
        self.assertIsNotNone(suites[1].find("testcase/skipped"))

    def test_report_text_names_the_selection_and_every_check(self):
        text = report.render_text(make_report(entry()))
        self.assertIn("selection:", text)
        self.assertIn("matched 1 of 1", text)
        self.assertIn("jech-loaded", text)
        self.assertIn("exit codes:", text)
        self.assertIn("0 = every check passed", text)

    def test_report_text_names_the_skip_reason_not_a_pass(self):
        text = report.render_text(make_report(skip_entry()))
        self.assertIn("[SKIP] skipped-env", text)
        self.assertIn("quick play needs 1.20+", text)

    def test_report_text_shows_the_world_seed_a_run_used(self):
        world = {"name": "jechworld", "savesPath": "saves/jechworld",
                 "digest": "a" * 64, "files": 7, "bytes": 4096,
                 "source": "vanilla-1.21.1", "reused": False}
        text = report.render_text(make_report(entry(world=world)))
        self.assertIn("world=jechworld", text)
        self.assertIn("seed=aaaaaaaaaaaaaaaa", text)
        self.assertIn("vanilla-1.21.1", text)

    def test_a_skipped_env_still_gets_a_junit_testcase(self):
        """An env with no checks must not vanish from the CI report."""
        skipped = skip_entry(checks=[])
        report.write_report(self.dir, make_report(skipped))
        root = ElementTree.fromstring((self.dir / report.JUNIT_XML).read_text(encoding="utf-8"))
        suite = root.find("testsuite")
        self.assertEqual(suite.attrib["tests"], "1")
        self.assertEqual(suite.attrib["skipped"], "1")
        skipped_case = suite.find("testcase/skipped")
        self.assertIsNotNone(skipped_case)
        self.assertIn("quick play needs 1.20+", skipped_case.attrib["message"])

    def test_junit_records_the_world_seed_as_a_property(self):
        world = {"name": "jechworld", "savesPath": "saves/jechworld", "digest": "b" * 64}
        report.write_report(self.dir, make_report(entry(world=world)))
        text = (self.dir / report.JUNIT_XML).read_text(encoding="utf-8")
        self.assertIn('name="world"', text)
        self.assertIn("jechworld@bbbbbbbbbbbbbbbb", text)

    def test_empty_selection_text_says_nothing_ran(self):
        built = report.new_report(run_id="unit", selection=Selection.parse(""),
                                  started_at="2026-01-01T00:00:00+0000")
        built["selection"]["explanation"] = Selection.parse("").explain([], 0)
        report.finalize(built, finished_at="2026-01-01T00:00:01+0000", duration_ms=1)
        text = report.render_text(built)
        self.assertIn("EMPTY", text)
        self.assertIn("never reported as a pass", text)


class VerifyGateTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = Path(self._tmp.name)

    def test_a_passing_report_verifies(self):
        report.write_report(self.tmp, make_report(entry()))
        self.assertEqual(report.verify_run(self.tmp, log=lambda *_: None), 0)

    def test_a_missing_report_is_a_failure_not_a_pass(self):
        with self.assertRaises(ReportError):
            report.load_report(self.tmp)

    def test_a_missing_directory_is_a_failure(self):
        with self.assertRaises(ReportError):
            report.load_report(self.tmp / "nope")

    def test_a_corrupt_report_is_a_failure(self):
        (self.tmp / report.REPORT_JSON).write_text("{not json", encoding="utf-8")
        with self.assertRaises(ReportError):
            report.load_report(self.tmp)

    def test_a_structurally_wrong_report_is_a_failure(self):
        (self.tmp / report.REPORT_JSON).write_text(json.dumps({"runId": "x"}), encoding="utf-8")
        with self.assertRaises(ReportError) as caught:
            report.load_report(self.tmp)
        self.assertIn("missing key", str(caught.exception))

    def test_a_failed_report_verifies_with_exit_code_one(self):
        failed = entry(status=checks.FAIL,
                       checks=[checks.result("jech-loaded", "log_contains", checks.FAIL, "missing")])
        report.write_report(self.tmp, make_report(failed))
        self.assertEqual(report.verify_run(self.tmp, log=lambda *_: None), 1)

    def test_a_watchdog_verifies_with_exit_code_three(self):
        killed = entry(status=checks.FAIL, watchdog=True, timedOut=True,
                       checks=[checks.result("internal:watchdog", "internal", checks.FAIL, "killed")])
        report.write_report(self.tmp, make_report(killed))
        self.assertEqual(report.verify_run(self.tmp, log=lambda *_: None), 3)

    def test_a_report_json_path_is_accepted(self):
        report.write_report(self.tmp, make_report(entry()))
        self.assertEqual(report.verify_run(self.tmp / report.REPORT_JSON, log=lambda *_: None), 0)


class CliTest(unittest.TestCase):
    def cli(self, argv):
        """Run the CLI with stdout/stderr captured, returning (code, output)."""
        buffer = io.StringIO()
        with contextlib.redirect_stdout(buffer), contextlib.redirect_stderr(buffer):
            code = main(argv)
        return code, buffer.getvalue()

    def test_envs_lists_the_shipped_matrix(self):
        with support.temp_home():
            code, output = self.cli(["envs"])
        self.assertEqual(code, 0)
        self.assertIn("1.21.1-neoforge", output)
        self.assertIn("mv-1.16.5-fabric", output)
        self.assertNotIn("example", output)
        self.assertIn("jech: local-build (local)", output)

    def test_envs_json_is_machine_readable(self):
        with support.temp_home():
            code, output = self.cli(["envs", "--json"])
        self.assertEqual(code, 0)
        listed = json.loads(output)
        self.assertGreaterEqual(len(listed), 16)
        for item in listed:
            self.assertIn("checks", item)
            self.assertIn("loader", item)

    def test_verify_without_a_report_is_exit_code_one(self):
        with support.temp_home() as home:
            code, output = self.cli(["verify", str(home / "runs" / "nothing")])
        self.assertEqual(code, 1)
        self.assertIn("report", output)
        self.assertIn("fail-closed", output)

    def test_verify_on_a_failed_report_is_exit_code_one(self):
        failed = entry(status=checks.FAIL,
                       checks=[checks.result("jech-loaded", "log_contains", checks.FAIL, "missing")])
        with support.temp_home() as home:
            run_dir = home / "runs" / "unit"
            report.write_report(run_dir, make_report(failed))
            code, output = self.cli(["verify", str(run_dir)])
        self.assertEqual(code, 1)
        self.assertIn("FAIL", output)

    def test_unknown_command_is_a_usage_error(self):
        with self.assertRaises(SystemExit):
            main(["nonsense"])


if __name__ == "__main__":
    unittest.main()
