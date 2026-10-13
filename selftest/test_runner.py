"""Runner bookkeeping: exit-code mapping, empty selections, crash snapshots."""

from __future__ import annotations

import json
import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock

from lib import checks, instance, paths, report, runner

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


class ReadinessRuleTest(unittest.TestCase):
    def test_an_env_without_ready_when_has_no_ready_values(self):
        ready, markers = runner.readiness_rule(support.mk_env())
        self.assertEqual(ready, [])
        self.assertEqual(markers, ["jecharacters"])

    def test_ready_when_and_every_log_contains_value_are_collected(self):
        env = support.mk_env(
            ready_when={"value": "Sound engine started"},
            checks=[{"type": "log_contains", "value": ["a", "b"]},
                    {"type": "no_crash_reports"},
                    {"type": "log_not_contains", "value": "nope"},
                    {"type": "log_regex", "value": "ignored"}],
        )
        ready, markers = runner.readiness_rule(env)
        self.assertEqual(ready, ["Sound engine started"])
        self.assertEqual(markers, ["a", "b"], "only log_contains gates readiness")

    def test_ready_line_seen(self):
        self.assertFalse(runner.ready_line_seen("anything", []))
        self.assertFalse(runner.ready_line_seen("anything", ["READY"]))
        self.assertTrue(runner.ready_line_seen("a\nREADY\n", ["READY"]))

    def test_all_markers_seen(self):
        self.assertTrue(runner.all_markers_seen("x", []))
        self.assertFalse(runner.all_markers_seen("x", ["M"]))
        self.assertTrue(runner.all_markers_seen("x\nM\n", ["M"]))


class _FakeFollower:
    """The minimum of :class:`instance.LogFollower` the watch loop uses."""

    def __init__(self, full_text):
        self._full = full_text
        self.text = ""
        self.session_started = False

    def wait_for_session(self, deadline, *, log=None, sleep=None):
        self.session_started = True
        return True

    def poll_once(self):
        self.text = self._full
        return self._full


class _FakeProc:
    def __init__(self, code=0, pid=4242):
        self.pid = pid
        self.returncode = code

    def poll(self):
        return self.returncode

    def wait(self, timeout=None):
        return self.returncode


class WatchLoopTest(unittest.TestCase):
    """The stopping rule: the runner ends the session itself once the env is ready."""

    def setUp(self):
        self._home = support.temp_home()
        self.home = self._home.__enter__()
        self.addCleanup(self._home.__exit__, None, None, None)

    def watch(self, text, *, ready, markers, grace=0.05):
        env = support.mk_env(
            ready_when={"value": ready[0]} if ready else None,
            checks=[{"type": "log_contains", "value": m} for m in markers] or
            [{"type": "no_crash_reports"}],
        )
        ready_values, marker_values = runner.readiness_rule(env)
        killed = {}

        def fake_kill(pids, *, log=print, grace=None):
            killed["pids"] = list(pids)
            return list(pids)

        with mock.patch.object(instance, "game_pids", return_value=[999]), \
                mock.patch.object(instance, "kill_process_tree", side_effect=fake_kill), \
                mock.patch.object(runner, "POLL_SEC", 0.01), \
                mock.patch.object(runner, "READY_GRACE_SEC", grace):
            result = runner._watch(env, "unit", _FakeProc(code=0), _FakeFollower(text),
                                   time.time(), log=lambda *_: None,
                                   ready_values=ready_values, markers=marker_values)
        return result, killed

    def test_the_runner_stops_once_the_ready_line_and_markers_are_in(self):
        watch, killed = self.watch("booting\nREADY\nMARKER\n", ready=["READY"], markers=["MARKER"])
        self.assertEqual(watch["stop_reason"], checks.STOP_GOAL_REACHED)
        self.assertTrue(watch["ready_reached"])
        self.assertTrue(watch["game_exited"])
        self.assertFalse(watch["timed_out"])
        self.assertEqual(watch["exit_code_source"], "runner")
        self.assertIn(999, killed["pids"], "the game's process tree must be ended by the runner")

    def test_a_marker_that_never_appears_bounds_the_run_and_stops_it(self):
        watch, _killed = self.watch("booting\nREADY\n", ready=["READY"], markers=["MARKER"])
        self.assertEqual(watch["stop_reason"], checks.STOP_READY_TIMEOUT)
        self.assertFalse(watch["ready_reached"])
        self.assertTrue(watch["game_exited"])
        self.assertFalse(watch["timed_out"])

    def test_a_ready_timeout_still_lets_exit_code_zero_pass(self):
        watch, _killed = self.watch("booting\nREADY\n", ready=["READY"], markers=["MARKER"])
        ctx = checks.CheckContext(stop_reason=watch["stop_reason"], exit_code=watch["exit_code"],
                                  exit_code_source=watch["exit_code_source"],
                                  game_started=True, game_exited=True, session_started=True,
                                  ready_reached=watch["ready_reached"])
        spec = support.mk_env(checks=[{"type": "exit_code_zero", "name": "exit-code"}]).checks[0]
        self.assertEqual(checks.evaluate_one(spec, ctx)["status"], checks.PASS)

    def test_without_ready_when_the_run_waits_for_a_natural_exit(self):
        env = support.mk_env()
        ready_values, markers = runner.readiness_rule(env)
        self.assertEqual(ready_values, [])
        calls = {"n": 0}

        def fake_pids(*args, **kwargs):
            calls["n"] += 1
            return [999] if calls["n"] == 1 else []

        with mock.patch.object(instance, "game_pids", side_effect=fake_pids), \
                mock.patch.object(instance, "kill_process_tree") as kill, \
                mock.patch.object(runner, "POLL_SEC", 0.01):
            watch = runner._watch(env, "unit", _FakeProc(code=0), _FakeFollower("booting\n"),
                                  time.time(), log=lambda *_: None,
                                  ready_values=ready_values, markers=markers)
        self.assertIsNone(watch["stop_reason"])
        self.assertTrue(watch["ready_reached"], "no ready_when means no readiness gate")
        self.assertFalse(watch["timed_out"])
        kill.assert_not_called()


if __name__ == "__main__":
    unittest.main()
