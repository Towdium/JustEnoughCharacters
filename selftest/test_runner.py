"""Runner bookkeeping: exit-code mapping, empty selections, crash snapshots."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from lib import checks, paths, report, runner

from . import support


class ExitCodeTest(unittest.TestCase):
    def test_the_mapping_is_the_documented_one(self):
        self.assertEqual(runner.env_exit_code({"status": checks.PASS, "exitCodeKind": "pass"}), 0)
        self.assertEqual(runner.env_exit_code({"status": checks.FAIL, "exitCodeKind": "check"}), 1)
        self.assertEqual(runner.env_exit_code({"status": checks.FAIL, "exitCodeKind": "config"}), 2)
        self.assertEqual(runner.env_exit_code({"status": checks.FAIL, "watchdog": True}), 3)
        self.assertEqual(runner.env_exit_code({"status": checks.FAIL, "timedOut": True}), 3)

    def test_a_config_error_that_still_passed_is_not_a_config_exit(self):
        self.assertEqual(runner.env_exit_code({"status": checks.PASS, "configError": True}), 0)


class EmptySelectionTest(unittest.TestCase):
    def setUp(self):
        self._home = support.temp_home()
        self.home = self._home.__enter__()
        self.addCleanup(self._home.__exit__, None, None, None)

    def run_selection(self, expression, exclude=None, envs=()):
        logs = []
        code = runner.run_selection(expression, exclude, envs=list(envs), log=logs.append)
        return code, logs

    def test_an_empty_expression_is_never_a_silent_pass(self):
        code, logs = self.run_selection("", envs=[support.mk_env()])
        self.assertEqual(code, 1)
        self.assertTrue(any("EMPTY" in line for line in logs), logs)
        run_dirs = sorted((paths.runs_dir()).iterdir())
        self.assertEqual(len(run_dirs), 1)
        written = json.loads((run_dirs[0] / report.REPORT_JSON).read_text(encoding="utf-8"))
        self.assertTrue(written["selection"]["empty"])
        self.assertEqual(written["envs"], [])
        self.assertEqual(written["status"], checks.FAIL)
        self.assertIn("matched no env", written["reason"])
        self.assertIn("EMPTY", written["selection"]["explanation"])

    def test_a_selection_matching_nothing_writes_a_failing_report(self):
        code, _logs = self.run_selection("tag:nope", envs=[support.mk_env()])
        self.assertEqual(code, 1)
        run_dirs = sorted(paths.runs_dir().iterdir())
        written = json.loads((run_dirs[0] / report.REPORT_JSON).read_text(encoding="utf-8"))
        self.assertIn("matched 0 of 1", written["selection"]["explanation"])
        self.assertEqual(report.exit_code_for(written), 1)

    def test_an_exclude_that_removes_everything_is_treated_the_same(self):
        code, _logs = self.run_selection("all", "tag:unit", envs=[support.mk_env()])
        self.assertEqual(code, 1)

    def test_no_env_at_all_still_fails_closed(self):
        code, _logs = self.run_selection("all", envs=[])
        self.assertEqual(code, 1)


class EnvReportShapeTest(unittest.TestCase):
    def test_a_single_env_report_carries_its_selection(self):
        env = support.mk_env()
        entry = {"name": env.name, "finishedAt": "2026-01-01T00:00:01+0000",
                 "status": checks.FAIL,
                 "checks": [checks.result("internal:run", "internal", checks.FAIL, "boom")]}
        built = runner._env_report("unit-run", env, entry, "2026-01-01T00:00:00+0000", 1000)
        self.assertEqual(built["envs"], [entry])
        self.assertEqual(built["selection"]["matched"], [env.name])
        self.assertEqual(built["status"], checks.FAIL)
        self.assertEqual(report.exit_code_for(built), 1)
        self.assertEqual(built["totals"]["checks"], 1)


class CrashSnapshotTest(unittest.TestCase):
    def test_snapshot_only_reports_files_that_exist(self):
        with tempfile.TemporaryDirectory() as tmp:
            minecraft = Path(tmp) / "minecraft"
            self.assertEqual(runner.crash_report_snapshot(minecraft), set())
            crashes = minecraft / "crash-reports"
            crashes.mkdir(parents=True)
            (crashes / "crash-2026-01-01_00.00.00-client.txt").write_text("boom", encoding="utf-8")
            (crashes / "notes").mkdir()
            self.assertEqual(runner.crash_report_snapshot(minecraft),
                             {"crash-2026-01-01_00.00.00-client.txt"})

    def test_new_crash_reports_are_the_difference(self):
        with tempfile.TemporaryDirectory() as tmp:
            minecraft = Path(tmp) / "minecraft"
            crashes = minecraft / "crash-reports"
            crashes.mkdir(parents=True)
            (crashes / "old.txt").write_text("old", encoding="utf-8")
            before = runner.crash_report_snapshot(minecraft)
            (crashes / "new.txt").write_text("new", encoding="utf-8")
            self.assertEqual(sorted(runner.crash_report_snapshot(minecraft) - before), ["new.txt"])


class TailTest(unittest.TestCase):
    def test_tail_returns_the_last_lines(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "launcher.log"
            path.write_text("\n".join(f"line {i}" for i in range(30)), encoding="utf-8")
            tail = runner.tail_of(path, lines=3)
            self.assertEqual(tail, "line 27\nline 28\nline 29")

    def test_tail_of_a_missing_file_is_empty(self):
        self.assertEqual(runner.tail_of("/nonexistent/launcher.log"), "")


if __name__ == "__main__":
    unittest.main()
