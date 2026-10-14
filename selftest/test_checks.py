"""Check evaluation, both polarities, plus the internal precondition checks."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from lib import checks
from lib import envs as envs_mod
from lib.errors import ConfigError

from . import support

LOG = (
    "[12:00:00] [main/INFO] Loading 1 mods\n"
    "[12:00:01] [main/INFO] jecharacters 4.6.8 for minecraft 1.21.1\n"
    "[12:00:02] [Render thread/WARN] something noisy\n"
)


def spec(type_, value=None, name=None):
    return envs_mod.CheckSpec(type_, name or type_, value, {})


def session(**kwargs):
    defaults = dict(log_text=LOG, log_path="logs/latest.log", session_started=True,
                    exit_code=0, exit_code_source="launcher", timed_out=False,
                    game_started=True, game_exited=True)
    defaults.update(kwargs)
    return checks.CheckContext(**defaults)


class DeclaredCheckTest(unittest.TestCase):
    def test_log_contains_passes_and_fails(self):
        self.assertEqual(checks.evaluate_one(spec("log_contains", "jecharacters"), session())["status"],
                         checks.PASS)
        failed = checks.evaluate_one(spec("log_contains", "jecharacters"), session(log_text="nothing"))
        self.assertEqual(failed["status"], checks.FAIL)
        self.assertIn("jecharacters", failed["detail"])

    def test_log_contains_accepts_a_list_and_needs_all_of_them(self):
        both = checks.evaluate_one(spec("log_contains", ["jecharacters", "minecraft"]), session())
        self.assertEqual(both["status"], checks.PASS)
        one = checks.evaluate_one(spec("log_contains", ["jecharacters", "absent-marker"]), session())
        self.assertEqual(one["status"], checks.FAIL)
        self.assertIn("absent-marker", one["detail"])

    def test_log_contains_skips_without_a_session(self):
        result = checks.evaluate_one(spec("log_contains", "jecharacters"), session(session_started=False))
        self.assertEqual(result["status"], checks.SKIP)
        self.assertIn("no game session", result["detail"])

    def test_log_not_contains(self):
        self.assertEqual(
            checks.evaluate_one(spec("log_not_contains", "Mixin apply failed"), session())["status"],
            checks.PASS)
        self.assertEqual(
            checks.evaluate_one(spec("log_not_contains", "something noisy"), session())["status"],
            checks.FAIL)

    def test_log_regex(self):
        self.assertEqual(
            checks.evaluate_one(spec("log_regex", r"jecharacters \d+\.\d+\.\d+"), session())["status"],
            checks.PASS)
        self.assertEqual(
            checks.evaluate_one(spec("log_regex", r"^jecharacters$"), session())["status"],
            checks.FAIL)

    def test_log_regex_multiline(self):
        self.assertEqual(checks.evaluate_one(spec("log_regex", r"^\[12:00:02\]"), session())["status"],
                         checks.PASS)

    def test_an_invalid_regex_is_a_config_error(self):
        with self.assertRaises(ConfigError):
            checks.evaluate_one(spec("log_regex", "[unclosed"), session())

    def test_no_crash_reports(self):
        self.assertEqual(checks.evaluate_one(spec("no_crash_reports"), session())["status"], checks.PASS)
        failed = checks.evaluate_one(spec("no_crash_reports"),
                                     session(new_crash_reports=["crash-2026-01-01.txt"]))
        self.assertEqual(failed["status"], checks.FAIL)
        self.assertIn("crash-2026-01-01.txt", failed["detail"])

    def test_no_crash_reports_skips_without_a_session(self):
        result = checks.evaluate_one(spec("no_crash_reports"), session(session_started=False))
        self.assertEqual(result["status"], checks.SKIP)

    def test_exit_code_zero(self):
        self.assertEqual(checks.evaluate_one(spec("exit_code_zero"), session())["status"], checks.PASS)
        self.assertEqual(
            checks.evaluate_one(spec("exit_code_zero"), session(exit_code=1))["status"], checks.FAIL)
        self.assertEqual(
            checks.evaluate_one(spec("exit_code_zero"), session(exit_code=None,
                                                                exit_code_source="unknown"))["status"],
            checks.FAIL)

    def test_exit_code_is_skipped_when_the_launcher_handed_off(self):
        result = checks.evaluate_one(spec("exit_code_zero"),
                                     session(exit_code=0, exit_code_source="handoff"))
        self.assertEqual(result["status"], checks.SKIP)
        self.assertIn("handoff", result["detail"])

    def test_a_deliberate_stop_passes_whatever_the_exit_code_says(self):
        """Once ready, the runner ends the game itself — the code is an artefact."""
        for reason in checks.DELIBERATE_STOP_REASONS:
            with self.subTest(reason=reason):
                result = checks.evaluate_one(spec("exit_code_zero"), session(
                    exit_code=-15, exit_code_source="runner", stop_reason=reason, ready_reached=False))
                self.assertEqual(result["status"], checks.PASS)
                self.assertIn(reason, result["detail"])

    def test_a_deliberate_stop_still_passes_on_top_of_a_handoff(self):
        result = checks.evaluate_one(spec("exit_code_zero"), session(
            exit_code=None, exit_code_source="handoff", stop_reason=checks.STOP_GOAL_REACHED))
        self.assertEqual(result["status"], checks.PASS)

    def test_a_natural_exit_before_the_ready_marker_fails(self):
        result = checks.evaluate_one(spec("exit_code_zero"), session(
            exit_code=0, exit_code_source="launcher", ready_reached=False))
        self.assertEqual(result["status"], checks.FAIL)
        self.assertIn("before the ready marker", result["detail"])

    def test_a_nonzero_natural_exit_after_readiness_fails(self):
        result = checks.evaluate_one(spec("exit_code_zero"), session(
            exit_code=1, exit_code_source="launcher", ready_reached=True))
        self.assertEqual(result["status"], checks.FAIL)

    def test_a_game_that_was_still_running_fails(self):
        result = checks.evaluate_one(spec("exit_code_zero"), session(
            exit_code=0, exit_code_source="launcher", game_exited=False))
        self.assertEqual(result["status"], checks.FAIL)
        self.assertIn("still running", result["detail"])

    def test_evaluating_a_list_keeps_the_order(self):
        results = checks.evaluate([spec("log_contains", "jecharacters", name="first"),
                                   spec("no_crash_reports", name="second")], session())
        self.assertEqual([item["name"] for item in results], ["first", "second"])


class InternalCheckTest(unittest.TestCase):
    def test_a_healthy_run_passes_every_internal_check(self):
        results = checks.internal_checks(session())
        self.assertTrue(all(item["status"] == checks.PASS for item in results),
                        [item["detail"] for item in results])

    def test_no_session_fails_the_session_check(self):
        results = checks.internal_checks(session(session_started=False, game_started=False,
                                                game_exited=False, exit_code=1))
        by_name = {item["name"]: item for item in results}
        self.assertEqual(by_name["internal:session-started"]["status"], checks.FAIL)
        self.assertEqual(by_name["internal:game-exit"]["status"], checks.FAIL)

    def test_a_watchdog_fails_its_check(self):
        results = checks.internal_checks(session(timed_out=True, exit_code=None,
                                                exit_code_source="unknown", game_exited=False,
                                                timeout_sec=600, duration_sec=601))
        by_name = {item["name"]: item for item in results}
        self.assertEqual(by_name["internal:watchdog"]["status"], checks.FAIL)
        self.assertIn("600", by_name["internal:watchdog"]["detail"])
        self.assertEqual(by_name["internal:game-exit"]["status"], checks.FAIL)

    def test_the_game_exit_detail_names_the_exit_code_source(self):
        results = checks.internal_checks(session(exit_code=0, exit_code_source="launcher"))
        detail = [item for item in results if item["name"] == "internal:game-exit"][0]["detail"]
        self.assertIn("source=launcher", detail)


class FileExistsTest(unittest.TestCase):
    """The one check that looks at the instance's filesystem instead of the log."""

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.minecraft = Path(self._tmp.name) / "minecraft"
        (self.minecraft / "config").mkdir(parents=True)
        (self.minecraft / "config" / "jecharacters.json").write_text("{}", encoding="utf-8")

    def ctx(self, **kwargs):
        defaults = dict(minecraft_dir=self.minecraft, session_started=True, game_started=True,
                        game_exited=True, exit_code=0, exit_code_source="launcher")
        defaults.update(kwargs)
        return checks.CheckContext(**defaults)

    def test_a_generated_config_file_passes(self):
        result = checks.evaluate_one(spec("file_exists", "config/jecharacters.json"), self.ctx())
        self.assertEqual(result["status"], checks.PASS)

    def test_a_missing_file_fails_and_names_it(self):
        result = checks.evaluate_one(spec("file_exists", "config/absent.json"), self.ctx())
        self.assertEqual(result["status"], checks.FAIL)
        self.assertIn("config/absent.json", result["detail"])

    def test_a_list_needs_every_entry(self):
        both = self.ctx()
        self.assertEqual(
            checks.evaluate_one(spec("file_exists", ["config/jecharacters.json"]), both)["status"],
            checks.PASS)
        partial = checks.evaluate_one(
            spec("file_exists", ["config/jecharacters.json", "config/absent.json"]), both)
        self.assertEqual(partial["status"], checks.FAIL)
        self.assertIn("config/absent.json", partial["detail"])

    def test_the_path_is_resolved_against_the_minecraft_dir(self):
        """A same-named file elsewhere on disk must not satisfy the check."""
        stray = Path(self._tmp.name) / "config"
        stray.mkdir()
        (stray / "jecharacters.json").write_text("{}", encoding="utf-8")
        result = checks.evaluate_one(spec("file_exists", "config/jecharacters.json"),
                                     self.ctx(minecraft_dir=self.minecraft / "nested"))
        self.assertEqual(result["status"], checks.FAIL)

    def test_without_an_instance_directory_it_skips(self):
        result = checks.evaluate_one(spec("file_exists", "config/jecharacters.json"),
                                     self.ctx(minecraft_dir=None))
        self.assertEqual(result["status"], checks.SKIP)
        self.assertIn("instance directory", result["detail"])

    def test_it_is_evaluated_even_when_no_session_started(self):
        """The filesystem is evidence in its own right: fail-closed, never a silent skip."""
        result = checks.evaluate_one(spec("file_exists", "config/absent.json"),
                                     self.ctx(session_started=False, game_started=False,
                                              game_exited=False))
        self.assertEqual(result["status"], checks.FAIL)


class UiTestReportTest(unittest.TestCase):
    """The harness's own verdict, read from the report it writes before it quits.

    The harness reports the same way jt does — a status plus totals — so the check
    is mostly about believing nothing else: a report that is missing, unreadable,
    a shape this version does not know, or that contradicts itself is a FAIL.
    """

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.minecraft = Path(self._tmp.name) / "minecraft"
        (self.minecraft / checks.UITEST_REPORT_DIR).mkdir(parents=True)

    def report(self, data, *, relative=None):
        path = self.minecraft / (relative or checks.UITEST_REPORT_DEFAULT)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(data if isinstance(data, str) else json.dumps(data), encoding="utf-8")
        return path

    def payload(self, *, status="PASS", failed=0, errored=0, scenarios=None, schema=None):
        return {
            "schema": checks.UITEST_REPORT_SCHEMA if schema is None else schema,
            "runId": "unit",
            "status": status,
            "totals": {"scenarios": 2, "passed": 2 - failed - errored, "failed": failed,
                       "errored": errored, "skipped": 0, "steps": 9, "checks": 6,
                       "checksFailed": 0, "captures": 3},
            "scenarios": scenarios if scenarios is not None else [
                {"name": "smoke", "status": "PASS", "message": None},
                {"name": "jei-pinyin-search", "status": "PASS", "message": None},
            ],
        }

    def ctx(self, **kwargs):
        defaults = dict(minecraft_dir=self.minecraft, session_started=True, game_started=True,
                        game_exited=True, exit_code=0, exit_code_source="launcher")
        defaults.update(kwargs)
        return checks.CheckContext(**defaults)

    def test_a_passing_report_passes_and_names_the_tally(self):
        self.report(self.payload())
        result = checks.evaluate_one(spec("uitest_report"), self.ctx())
        self.assertEqual(result["status"], checks.PASS)
        self.assertIn("2/2 scenarios", result["detail"])
        self.assertIn("6/6 checks", result["detail"])
        self.assertIn(checks.UITEST_CAPTURES_DIR, result["detail"])

    def test_a_failing_report_fails_and_names_the_scenario(self):
        scenarios = [
            {"name": "smoke", "status": "PASS", "message": None},
            {"name": "jei-pinyin-search", "status": "FAIL",
             "message": "assert_match_recorded candidate_contains 石"},
        ]
        self.report(self.payload(status="FAIL", failed=1, scenarios=scenarios))
        result = checks.evaluate_one(spec("uitest_report"), self.ctx())
        self.assertEqual(result["status"], checks.FAIL)
        self.assertIn("jei-pinyin-search=FAIL", result["detail"])
        self.assertIn("candidate_contains", result["detail"])

    def test_a_report_that_is_not_there_fails_rather_than_skips(self):
        result = checks.evaluate_one(spec("uitest_report"), self.ctx())
        self.assertEqual(result["status"], checks.FAIL)
        self.assertIn(checks.UITEST_REPORT_DEFAULT, result["detail"])

    def test_a_report_that_cannot_be_read_fails(self):
        self.report("{not json")
        result = checks.evaluate_one(spec("uitest_report"), self.ctx())
        self.assertEqual(result["status"], checks.FAIL)
        self.assertIn("could not be read as JSON", result["detail"])

    def test_a_report_from_a_schema_this_check_does_not_know_fails(self):
        self.report(self.payload(schema=2))
        result = checks.evaluate_one(spec("uitest_report"), self.ctx())
        self.assertEqual(result["status"], checks.FAIL)
        self.assertIn("schema", result["detail"])

    def test_totals_that_disagree_with_the_status_fail(self):
        self.report(self.payload(status="PASS", failed=1))
        result = checks.evaluate_one(spec("uitest_report"), self.ctx())
        self.assertEqual(result["status"], checks.FAIL)

    def test_a_report_without_readable_totals_fails(self):
        self.report({"schema": checks.UITEST_REPORT_SCHEMA, "status": "PASS", "totals": {}})
        result = checks.evaluate_one(spec("uitest_report"), self.ctx())
        self.assertEqual(result["status"], checks.FAIL)
        self.assertIn("failed/errored", result["detail"])

    def test_an_explicit_path_is_resolved_against_the_minecraft_dir(self):
        self.report(self.payload(), relative="other/report.json")
        result = checks.evaluate_one(spec("uitest_report", "other/report.json"), self.ctx())
        self.assertEqual(result["status"], checks.PASS)
        stray = Path(self._tmp.name) / "other"
        stray.mkdir()
        (stray / "report.json").write_text(json.dumps(self.payload()), encoding="utf-8")
        result = checks.evaluate_one(spec("uitest_report", "other/report.json"),
                                     self.ctx(minecraft_dir=self.minecraft / "nested"))
        self.assertEqual(result["status"], checks.FAIL)

    def test_without_an_instance_directory_it_skips(self):
        result = checks.evaluate_one(spec("uitest_report"), self.ctx(minecraft_dir=None))
        self.assertEqual(result["status"], checks.SKIP)
        self.assertIn("instance directory", result["detail"])

    def test_it_is_evaluated_even_when_no_session_started(self):
        """A crashed client is exactly the case this check exists for."""
        result = checks.evaluate_one(spec("uitest_report"),
                                     self.ctx(session_started=False, game_started=False,
                                              game_exited=False))
        self.assertEqual(result["status"], checks.FAIL)


class RollUpTest(unittest.TestCase):
    def test_any_failure_fails_the_run(self):
        good = checks.result("a", "log_contains", checks.PASS, "")
        bad = checks.result("b", "log_contains", checks.FAIL, "")
        skipped = checks.result("c", "log_contains", checks.SKIP, "")
        self.assertEqual(checks.overall_status([good]), checks.PASS)
        self.assertEqual(checks.overall_status([good, bad]), checks.FAIL)
        self.assertEqual(checks.overall_status([good, skipped]), checks.PASS)

    def test_summary_counts(self):
        items = [checks.result("a", "t", checks.PASS, ""), checks.result("b", "t", checks.FAIL, ""),
                 checks.result("c", "t", checks.SKIP, "")]
        self.assertEqual(checks.summarize(items),
                         {"checks": 3, "passed": 1, "failed": 1, "skipped": 1})


if __name__ == "__main__":
    unittest.main()
