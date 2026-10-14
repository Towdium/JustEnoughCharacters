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

    def test_a_declared_skip_exits_one_and_is_never_a_pass(self):
        self.assertEqual(runner.env_exit_code({"status": checks.SKIP, "exitCodeKind": "skip"}), 1)
        self.assertEqual(runner.env_exit_code({"status": checks.SKIP}), 1)

    def test_a_config_error_that_still_passed_is_not_a_config_exit(self):
        self.assertEqual(runner.env_exit_code({"status": checks.PASS, "configError": True}), 0)


class SkippedEnvTest(unittest.TestCase):
    """A declared skip is reported, never executed, and never a pass."""

    def setUp(self):
        self._home = support.temp_home()
        self.home = self._home.__enter__()
        self.addCleanup(self._home.__exit__, None, None, None)
        self.env = support.mk_env(skip="needs a world load, and quickPlay needs 1.20+")

    def test_the_env_is_not_executed_and_the_report_says_so(self):
        logs = []
        entry, code = runner.run_env(self.env, log=logs.append)
        self.assertEqual(code, 1, "a skip must not be exit code 0")
        self.assertEqual(entry["status"], checks.SKIP)
        self.assertEqual(entry["exitCodeKind"], "skip")
        self.assertEqual(entry["skipReason"], self.env.skip)
        self.assertEqual(entry["mods"], [])
        self.assertTrue(any("SKIP unit-env" in line for line in logs), logs)
        run_dir = sorted(paths.runs_dir().iterdir())[0]
        self.assertEqual(run_dir.name, entry["runDir"].rsplit("/", 1)[-1])
        written = json.loads((run_dir / report.REPORT_JSON).read_text(encoding="utf-8"))
        self.assertEqual(written["status"], checks.FAIL)
        self.assertEqual(written["totals"]["skipped"], 1)
        self.assertEqual(written["totals"]["passed"], 0)
        self.assertIn("declared skip", written["reason"])
        self.assertEqual(written["envs"][0]["status"], checks.SKIP)
        text = (run_dir / report.REPORT_TXT).read_text(encoding="utf-8")
        self.assertIn("[SKIP] unit-env", text)
        self.assertIn("quickPlay needs 1.20+", text)
        self.assertIn("were NOT executed", text)
        self.assertEqual(report.verify_run(run_dir, log=lambda *_: None), 1)

    def test_a_skipped_env_launches_nothing_and_leaves_no_instance_state(self):
        runner.run_env(self.env, log=lambda *_: None)
        self.assertFalse((self.home / "prism").exists(),
                         "there is no instance data root any more")
        self.assertFalse((self.home / "state.json").exists(),
                         "nothing is mapped to an instance any more")

    def test_a_selection_of_only_skipped_envs_is_red(self):
        other = support.mk_env(name="other-env", skip="same reason")
        code = runner.run_selection("all", envs=[self.env, other], log=lambda *_: None)
        self.assertEqual(code, 1)
        run_dirs = sorted(paths.runs_dir().iterdir())
        written = json.loads((run_dirs[0] / report.REPORT_JSON).read_text(encoding="utf-8"))
        self.assertEqual(written["totals"], {"envs": 2, "passed": 0, "failed": 0, "skipped": 2,
                                            "checks": 2, "checksFailed": 0, "checksSkipped": 2,
                                            "watchdogs": 0})
        self.assertEqual(report.exit_code_for(written), 1)


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


class _StagedFollower:
    """A log that fills in one stage per poll — the shape of a late write.

    ``_FakeFollower`` shows the whole text from the first poll, which cannot
    express "the ready line is there, the crash report lands a moment later".
    """

    def __init__(self, stages):
        self._stages = list(stages)
        self.text = ""
        self.session_started = False
        self.polls = 0

    def wait_for_session(self, deadline, *, log=None, sleep=None):
        self.session_started = True
        return True

    def poll_once(self):
        self.polls += 1
        if self._stages:
            self.text += self._stages.pop(0)
        return self.text


class WatchLoopTest(unittest.TestCase):
    """The stopping rule: the runner ends the session itself once the env is ready."""

    def setUp(self):
        self._home = support.temp_home()
        self.home = self._home.__enter__()
        self.addCleanup(self._home.__exit__, None, None, None)

    def watch(self, text, *, ready, markers, grace=0.05, timeout_sec=600, settle=0.05,
              follower=None, started_wall=None, pids=(999,), pids_fn=None):
        env = support.mk_env(
            ready_when={"value": ready[0]} if ready else None,
            checks=[{"type": "log_contains", "value": m} for m in markers] or
            [{"type": "no_crash_reports"}],
        )
        # The schema floor for timeout_sec is 30s; a test that needs a shorter truth
        # sets the attribute on the parsed env instead of fighting the validator.
        env.timeout_sec = timeout_sec
        ready_values, marker_values = runner.readiness_rule(env)
        killed = {}

        def fake_kill(pids, *, log=print, grace=None):
            killed["pids"] = list(pids)
            return list(pids)

        follower = follower if follower is not None else _FakeFollower(text)
        seen = pids_fn if pids_fn is not None else (lambda: list(pids))
        with mock.patch.object(instance, "kill_process_tree", side_effect=fake_kill), \
                mock.patch.object(runner, "POLL_SEC", 0.01), \
                mock.patch.object(runner, "SETTLE_GRACE_SEC", settle), \
                mock.patch.object(runner, "READY_GRACE_SEC", grace):
            result = runner._watch(env, _FakeProc(code=0), follower,
                                   started_wall if started_wall is not None else time.time(),
                                   log=lambda *_: None, pids_fn=seen,
                                   ready_values=ready_values, markers=marker_values)
        return result, killed

    def test_the_runner_stops_once_the_ready_line_and_markers_are_in(self):
        watch, killed = self.watch("booting\nREADY\nMARKER\n", ready=["READY"], markers=["MARKER"])
        self.assertEqual(watch["stop_reason"], checks.STOP_GOAL_REACHED)
        self.assertTrue(watch["ready_reached"])
        self.assertTrue(watch["ready_line_reached"])
        self.assertEqual(watch["missing_markers"], [])
        self.assertTrue(watch["game_exited"])
        self.assertFalse(watch["timed_out"])
        self.assertEqual(watch["exit_code_source"], "runner")
        self.assertIn(999, killed["pids"], "the game's process tree must be ended by the runner")

    def test_the_stop_settles_so_late_evidence_is_captured(self):
        """The client keeps writing for a moment after the ready line.

        Measured on the 1.16.5-forge row: FML writes its mod-loading crash report
        (and the `Crash report saved to …` line) 0.37s *after* `Sound engine
        started`. Ending the process at the ready line raced that write — the same
        env PASSed once and FAILed once on identical inputs — so the stop waits
        ``SETTLE_GRACE_SEC`` for it.
        """
        follower = _StagedFollower(["booting\nREADY\n", "", "Crash report saved to x\n"])
        watch, _killed = self.watch("", ready=["READY"], markers=[], settle=0.3,
                                    follower=follower)
        self.assertEqual(watch["stop_reason"], checks.STOP_GOAL_REACHED)
        self.assertGreater(follower.polls, 5, "the stop must wait out the settle, not fire at once")
        self.assertIn("Crash report saved to x", follower.text,
                      "evidence written just after the ready line must still land")

    def test_a_game_that_is_already_gone_does_not_wait_the_settle(self):
        """Nothing more can arrive from a dead process, so the settle is skipped."""
        follower = _StagedFollower(["booting\nREADY\n"])
        seen = {"n": 0}

        def pids_fn():
            seen["n"] += 1
            return [999] if seen["n"] == 1 else []

        watch, _killed = self.watch("", ready=["READY"], markers=[], settle=30,
                                    follower=follower, pids_fn=pids_fn)
        self.assertEqual(watch["stop_reason"], checks.STOP_GOAL_REACHED)
        self.assertEqual(follower.polls, 2, "a gone game stops the run on its first poll")

    def test_a_marker_that_never_appears_bounds_the_run_and_stops_it(self):
        watch, _killed = self.watch("booting\nREADY\n", ready=["READY"], markers=["MARKER"])
        self.assertEqual(watch["stop_reason"], checks.STOP_READY_TIMEOUT)
        self.assertFalse(watch["ready_reached"])
        # The ready line was there — the *marker* was the half that failed. That
        # distinction is exactly what the report has to carry.
        self.assertTrue(watch["ready_line_reached"])
        self.assertEqual(watch["missing_markers"], ["MARKER"])
        self.assertTrue(watch["game_exited"])
        self.assertFalse(watch["timed_out"])

    def test_a_ready_line_that_never_appears_is_visible_as_such(self):
        # A ready line that never lands is bounded by the watchdog, so the env gets
        # a short budget here: the point is the reported halves, not the waiting.
        watch, _killed = self.watch("booting\nMARKER\n", ready=["READY"], markers=["MARKER"],
                                    timeout_sec=1)
        self.assertTrue(watch["timed_out"])
        self.assertIsNone(watch["stop_reason"])
        self.assertFalse(watch["ready_reached"])
        self.assertFalse(watch["ready_line_reached"])
        self.assertEqual(watch["missing_markers"], [])

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

        with mock.patch.object(instance, "kill_process_tree") as kill, \
                mock.patch.object(runner, "POLL_SEC", 0.01):
            watch = runner._watch(env, _FakeProc(code=0), _FakeFollower("booting\n"),
                                  time.time(), log=lambda *_: None, pids_fn=fake_pids,
                                  ready_values=ready_values, markers=markers)
        self.assertIsNone(watch["stop_reason"])
        self.assertTrue(watch["ready_reached"], "no ready_when means no readiness gate")
        self.assertFalse(watch["timed_out"])
        kill.assert_not_called()


class _AliveProc:
    """A launcher that is still running (and answers -15 once it is ended)."""

    def __init__(self, pid=4242):
        self.pid = pid

    def poll(self):
        return None

    def wait(self, timeout=None):
        return -15


class _NoSessionFollower:
    """A log that never becomes a session, so readiness can never stop the loop."""

    def __init__(self):
        self.text = ""
        self.session_started = False

    def wait_for_session(self, deadline, *, log=None, sleep=None):
        return False

    def poll_once(self):
        return ""


class LauncherExitTest(unittest.TestCase):
    """The launcher is this run's own Gradle invocation, and it is never left behind.

    A launcher that exits without producing a game is a failed launch, not a
    handoff: nothing else can start that client, so the run gives up at once
    instead of waiting out a grace for a launch that is never coming. A launcher
    that is still alive when the run gives up is ended here — a Gradle daemon plus
    the client it keeps alive is exactly the memory this machine does not have.
    """

    def setUp(self):
        self._home = support.temp_home()
        self.home = self._home.__enter__()
        self.addCleanup(self._home.__exit__, None, None, None)
        self.env = support.mk_env(ready_when={"value": "READY"},
                                  checks=[{"type": "log_contains", "value": "MARKER"}])
        self.ready_values, self.markers = runner.readiness_rule(self.env)

    def watch(self, *, proc, pids_sequence, timeout_sec=5):
        calls = {"n": 0}

        def pids_fn():
            index = min(calls["n"], len(pids_sequence) - 1)
            calls["n"] += 1
            return list(pids_sequence[index])

        self.env.timeout_sec = timeout_sec
        killed = []
        with mock.patch.object(instance, "kill_process_tree",
                               side_effect=lambda pids, **kw: killed.append(list(pids))), \
                mock.patch.object(runner, "POLL_SEC", 0.01):
            watch = runner._watch(self.env, proc, _NoSessionFollower(), time.time(),
                                  log=lambda *_: None, pids_fn=pids_fn,
                                  ready_values=self.ready_values, markers=self.markers)
        return watch, killed

    def test_a_launcher_that_exits_without_a_game_gives_up_at_once(self):
        watch, killed = self.watch(proc=_FakeProc(code=1), pids_sequence=[[]])
        self.assertFalse(watch["game_started"])
        self.assertFalse(watch["timed_out"], "a failed launch is not a watchdog timeout")
        self.assertEqual(watch["exit_code"], 1)
        self.assertEqual(watch["exit_code_source"], "launcher")
        self.assertTrue(any("no game process" in note for note in watch["notes"]), watch["notes"])
        self.assertFalse(any("waiting up to" in note for note in watch["notes"]),
                         "there is nothing to hand a launch off to any more")
        self.assertEqual(killed, [], "the launcher already exited")

    def test_a_launcher_that_outlives_the_run_is_ended(self):
        watch, killed = self.watch(proc=_AliveProc(), pids_sequence=[[]], timeout_sec=0.05)
        self.assertFalse(watch["game_started"])
        self.assertTrue(watch["timed_out"])
        self.assertEqual(killed, [[4242]], "the launcher must not be left running")

    def test_a_launcher_that_exits_while_the_game_keeps_running_is_detached(self):
        """The game is not the wrapper's child any more, so its code is unobservable."""
        watch, _killed = self.watch(proc=_FakeProc(code=0), pids_sequence=[[999], [], []])
        self.assertTrue(watch["game_started"])
        self.assertIsNone(watch["stop_reason"])
        self.assertEqual(watch["exit_code_source"], "detached")
        self.assertTrue(watch["game_exited"])


if __name__ == "__main__":
    unittest.main()
