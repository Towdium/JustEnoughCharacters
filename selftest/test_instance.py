"""The game directory, its isolation, the java lookup and the launch layer."""

from __future__ import annotations

import json
import os
import tempfile
import time
import unittest
from unittest import mock

from lib import instance, pack, paths
from lib.errors import ConfigError

from . import support


class JavaLookupTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = paths.Path(self._tmp.name)
        self.home = self.tmp / "jdk-21"
        (self.home / "bin").mkdir(parents=True)
        (self.home / "bin" / "java").write_text("#!/bin/sh\n", encoding="utf-8")
        (self.home / "release").write_text('IMPLEMENTOR="x"\nJAVA_VERSION="21.0.5"\n', encoding="utf-8")
        self.command = f'{os.sys.executable} -c "print(\'{self.tmp}/jdk-{{version}}\')"'

    def test_the_lookup_command_is_rendered_with_the_version(self):
        found = instance.java_home_for(21, command=self.command)
        self.assertEqual(found, self.home)

    def test_a_bogus_result_is_rejected(self):
        command = f'{os.sys.executable} -c "print(\'{self.tmp}/nothing-{{version}}\')"'
        with self.assertRaises(ConfigError):
            instance.java_home_for(21, command=command)

    def test_a_failing_command_is_an_error(self):
        command = f'{os.sys.executable} -c "raise SystemExit(1)" # {{version}}'
        with self.assertRaises(ConfigError):
            instance.java_home_for(21, command=command)

    def test_major_version_is_read_from_the_release_file(self):
        self.assertEqual(instance.read_java_major(self.home), 21)

    def test_env_var_fallback_when_no_helper_exists(self):
        with mock.patch.dict(os.environ, {"JT_JAVA_21_HOME": str(self.home)}, clear=False):
            self.assertEqual(instance.java_home_for(21, command=self.tmp / "absent-helper"), self.home)

    def test_mismatched_java_home_is_rejected(self):
        with mock.patch.dict(os.environ, {"JAVA_HOME": str(self.home)}, clear=True):
            with self.assertRaises(ConfigError):
                instance.java_home_for(17, command=self.tmp / "absent-helper")

    def test_no_jdk_at_all_is_an_explicit_error(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(ConfigError) as caught:
                instance.java_home_for(21, command=self.tmp / "absent-helper")
        self.assertIn("JT_JAVA_21_HOME", str(caught.exception))


class JavaQueryTest(unittest.TestCase):
    def test_java8_is_asked_for_as_1_8_too(self):
        """macOS java_home matches the release string, so Java 8 answers to '1.8'."""
        self.assertEqual(list(instance.java_version_queries(8)), ["8", "1.8"])
        self.assertEqual(list(instance.java_version_queries(17)), ["17"])
        self.assertEqual(list(instance.java_version_queries(21)), ["21"])

    def test_a_helper_that_returns_the_wrong_major_is_not_trusted(self):
        """``java_home -v 8`` can fall back to the default JDK instead of failing."""
        with tempfile.TemporaryDirectory() as tmp:
            root = paths.Path(tmp)
            wrong = root / "jdk-25"
            (wrong / "bin").mkdir(parents=True)
            (wrong / "bin" / "java").write_text("#!/bin/sh\n", encoding="utf-8")
            (wrong / "release").write_text('JAVA_VERSION="25.0.3"\n', encoding="utf-8")
            # a helper that always answers with the (wrong) JDK
            command = f'{os.sys.executable} -c "print(\'{wrong}\')" # {{version}}'
            with self.assertRaises(ConfigError) as caught:
                instance.java_home_for(8, command=command)
        self.assertIn("JT_JAVA_8_HOME", str(caught.exception))

    def test_the_1_8_form_is_tried_when_the_bare_form_mismatches(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = paths.Path(tmp)
            eight = root / "azul-1.8"
            (eight / "bin").mkdir(parents=True)
            (eight / "bin" / "java").write_text("#!/bin/sh\n", encoding="utf-8")
            (eight / "release").write_text('JAVA_VERSION="1.8.0_492"\n', encoding="utf-8")
            wrong = root / "jbr-25"
            (wrong / "bin").mkdir(parents=True)
            (wrong / "bin" / "java").write_text("#!/bin/sh\n", encoding="utf-8")
            (wrong / "release").write_text('JAVA_VERSION="25.0.3"\n', encoding="utf-8")
            # '8' -> the wrong JDK, '1.8' -> the right one
            script = (f'import sys; print(r"{wrong}" if sys.argv[1] == "8" else r"{eight}")')
            command = f'{os.sys.executable} -c {json.dumps(script)} {{version}}'
            self.assertEqual(instance.java_home_for(8, command=command), eight)


class IsolationTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = paths.Path(self._tmp.name)
        self.run_dir = self.tmp / "runs" / "unit"
        self.minecraft = instance.run_game_dir(self.run_dir)
        (self.minecraft / "mods").mkdir(parents=True)
        (self.minecraft / "config").mkdir(parents=True)
        (self.minecraft / "saves").mkdir(parents=True)
        (self.minecraft / "logs").mkdir(parents=True)
        (self.minecraft / "mods" / "stale.jar").write_text("old", encoding="utf-8")
        (self.minecraft / "mods" / "readme.txt").write_text("old", encoding="utf-8")
        (self.minecraft / "config" / "jecharacters.toml").write_text("pinyin=true\n", encoding="utf-8")
        (self.minecraft / "saves" / "world").mkdir()
        (self.minecraft / "logs" / "latest.log").write_text("previous session\n", encoding="utf-8")
        self.jar = support.write_jar(self.tmp / "jei.jar", mod_id="jei")
        self.resolved = support.resolved_env(support.mk_env(), [support.artifact("jei", self.jar)])

    def isolate(self, resolved=None, **kwargs):
        return instance.isolate("unit-env", resolved or self.resolved, self.run_dir,
                                game_dir_path=self.minecraft, log=lambda *_: None, **kwargs)

    def test_mods_are_resynced_from_scratch(self):
        report = self.isolate()
        present = sorted(p.name for p in (self.minecraft / "mods").iterdir())
        self.assertEqual(present, ["jei.jar"])
        self.assertEqual(report.removed, 2)
        self.assertEqual(report.staged, ["jei.jar"])

    def test_config_is_archived_then_emptied(self):
        report = self.isolate()
        self.assertEqual(report.config_archived, 1)
        self.assertEqual(list((self.minecraft / "config").iterdir()), [])
        archived = self.run_dir / "pre-run-config" / "jecharacters.toml"
        self.assertTrue(archived.is_file())
        self.assertEqual(archived.read_text(encoding="utf-8"), "pinyin=true\n")

    def test_saves_and_logs_are_emptied(self):
        self.isolate()
        self.assertEqual(list((self.minecraft / "saves").iterdir()), [])
        self.assertEqual(list((self.minecraft / "logs").iterdir()), [])

    def test_the_isolation_report_serialises(self):
        report = self.isolate()
        payload = report.to_json()
        self.assertEqual(payload["modsStaged"], ["jei.jar"])
        self.assertEqual(payload["savesCleared"], 1)
        self.assertIn("mods:", report.summary())

    def test_a_dev_classpath_artifact_is_not_staged_and_says_so(self):
        """A jar the checkout's own run already carries must not be copied in.

        Staging it would put a second copy of the same mod id in front of the
        loader — a double load, not a second opinion.
        """
        dev_jar = support.write_jar(self.tmp / "jech.jar", mod_id="jecharacters")
        artifact = support.artifact("jech", dev_jar)
        artifact.via = "dev-classpath"
        resolved = support.resolved_env(support.mk_env(),
                                        [support.artifact("jei", self.jar), artifact])
        report = self.isolate(resolved)
        self.assertEqual(report.staged, ["jei.jar"])
        present = sorted(p.name for p in (self.minecraft / "mods").iterdir())
        self.assertEqual(present, ["jei.jar"], "the dev-classpath jar must not land in mods/")
        self.assertTrue(any("dev-classpath" in note for note in report.notes), report.notes)

    def test_isolation_touches_only_the_game_directory_it_was_given(self):
        """One game directory per run: another run's directory stays untouched."""
        other = instance.run_game_dir(self.tmp / "runs" / "other")
        (other / "mods").mkdir(parents=True)
        (other / "mods" / "stale.jar").write_text("old", encoding="utf-8")
        report = self.isolate()
        self.assertEqual(report.mods_dir, self.minecraft / "mods")
        self.assertEqual(sorted(p.name for p in (self.minecraft / "mods").iterdir()), ["jei.jar"])
        self.assertEqual(sorted(p.name for p in (other / "mods").iterdir()), ["stale.jar"],
                         "another run's game directory must stay untouched")


class OverrideStagingTest(unittest.TestCase):
    """The pack's non-mod overrides (options.txt, the world seed) are re-staged every run."""

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = paths.Path(self._tmp.name)
        self.run_dir = self.tmp / "runs" / "unit"
        self.minecraft = instance.run_game_dir(self.run_dir)
        for name in ("mods", "config", "saves", "logs"):
            (self.minecraft / name).mkdir(parents=True, exist_ok=True)
        self.jar = support.write_jar(self.tmp / "jei.jar", mod_id="jei")
        self.resolved = support.resolved_env(support.mk_env(), [support.artifact("jei", self.jar)])

    def pack_result(self, *, overrides=("options.txt", "saves/jechworld"), digest="d" * 64):
        pack_dir = self.run_dir / "pack"
        overrides_dir = pack_dir / "overrides"
        (overrides_dir / "mods").mkdir(parents=True, exist_ok=True)
        if "options.txt" in overrides:
            (overrides_dir / "options.txt").write_text("onboardAccessibility:true\n", encoding="utf-8")
        if "saves/jechworld" in overrides:
            world = overrides_dir / "saves" / "jechworld"
            (world / "region").mkdir(parents=True, exist_ok=True)
            (world / "level.dat").write_bytes(b"seed level")
            (world / "region" / "r.0.0.mca").write_bytes(b"seed region")
        return pack.PackResult(
            pack_dir=pack_dir, index_path=pack_dir / "modrinth.index.json",
            mrpack_path=self.run_dir / "unit.mrpack", pack_hash="deadbeef",
            mod_filenames=[], version_id="unit", dependencies={},
            extra_overrides=list(overrides), world="jechworld" if "saves/jechworld" in overrides else None,
            world_digest=digest,
        )

    def isolate(self, pack_result):
        return instance.isolate("unit-env", self.resolved, self.run_dir,
                                game_dir_path=self.minecraft, pack_result=pack_result,
                                log=lambda *_: None)

    def test_the_options_preset_and_the_world_land_in_the_game_directory(self):
        report = self.isolate(self.pack_result())
        self.assertEqual((self.minecraft / "options.txt").read_text(encoding="utf-8"),
                         "onboardAccessibility:true\n")
        self.assertEqual((self.minecraft / "saves" / "jechworld" / "level.dat").read_bytes(),
                         b"seed level")
        self.assertEqual(report.overrides_staged, ["options.txt", "saves/jechworld"])
        self.assertIn("saves/jechworld", report.summary())
        self.assertIn("saves/jechworld", report.to_json()["overridesStaged"])

    def test_a_world_a_previous_run_played_in_is_replaced_by_the_seed(self):
        stale = self.minecraft / "saves" / "jechworld"
        (stale / "region").mkdir(parents=True)
        (stale / "level.dat").write_bytes(b"stale level")
        (stale / "playerdata").mkdir(parents=True)
        (self.minecraft / "options.txt").write_text("onboardAccessibility:false\n", encoding="utf-8")
        self.isolate(self.pack_result())
        self.assertEqual((stale / "level.dat").read_bytes(), b"seed level")
        self.assertFalse((stale / "playerdata").exists(), "the staged world must not merge")
        self.assertEqual((self.minecraft / "options.txt").read_text(encoding="utf-8"),
                         "onboardAccessibility:true\n")

    def test_a_pack_without_a_world_stages_only_the_preset(self):
        report = self.isolate(self.pack_result(overrides=("options.txt",)))
        self.assertEqual(report.overrides_staged, ["options.txt"])
        self.assertEqual(sorted(p.name for p in (self.minecraft / "saves").iterdir()), [])

    def test_a_declared_override_missing_from_the_pack_is_an_error(self):
        result = self.pack_result()
        (result.overrides_dir() / "options.txt").unlink()
        with self.assertRaises(ConfigError):
            self.isolate(result)

    def test_isolation_without_a_pack_result_behaviour_is_unchanged(self):
        report = instance.isolate("unit-env", self.resolved, self.run_dir,
                                  game_dir_path=self.minecraft, log=lambda *_: None)
        self.assertEqual(report.overrides_staged, [])
        self.assertFalse((self.minecraft / "options.txt").exists())


class GradleLaunchTest(unittest.TestCase):
    """The launch layer's command line, game directory and log location."""

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = paths.Path(self._tmp.name)
        self.checkout = self.tmp / "1.21.1"
        (self.checkout / "gradlew").parent.mkdir(parents=True, exist_ok=True)
        (self.checkout / "gradlew").write_text("#!/bin/sh\n", encoding="utf-8")

    def test_the_command_names_the_task_the_game_dir_and_no_daemon(self):
        command = instance.gradle_command(self.checkout, ":neoforge:runClient",
                                          self.tmp / "game")
        self.assertEqual(command, [
            str(self.checkout / "gradlew"), ":neoforge:runClient", "--no-daemon",
            f"-P{instance.GRADLE_RUN_DIR_PROPERTY}={self.tmp / 'game'}",
        ])
        self.assertNotIn(f"-P{instance.GRADLE_QUICK_PLAY_PROPERTY}", " ".join(command))

    def test_a_world_is_passed_as_a_project_property(self):
        command = instance.gradle_command(self.checkout, ":fabric:runClient", self.tmp / "game",
                                          world="jechworld")
        self.assertIn(f"-P{instance.GRADLE_QUICK_PLAY_PROPERTY}=jechworld", command)

    def test_a_ui_test_selection_is_passed_as_one_project_property(self):
        """The selection goes over as one argv element, commas and all."""
        command = instance.gradle_command(self.checkout, ":neoforge:runClientTests",
                                          self.tmp / "game", uitest="tag:smoke,jei")
        self.assertIn(f"-P{instance.GRADLE_UITEST_PROPERTY}=tag:smoke,jei", command)

    def test_without_a_selection_no_ui_test_property_is_passed(self):
        """An ordinary row must not switch the harness on by accident."""
        command = instance.gradle_command(self.checkout, ":neoforge:runClient", self.tmp / "game")
        self.assertNotIn(f"-P{instance.GRADLE_UITEST_PROPERTY}", " ".join(command))

    def test_the_game_dir_is_under_the_run_directory(self):
        self.assertEqual(instance.run_game_dir(self.tmp / "runs" / "rid"),
                         self.tmp / "runs" / "rid" / "game")

    def test_the_log_lives_in_the_game_directory_not_the_checkout(self):
        game = instance.run_game_dir(self.tmp / "runs" / "rid")
        self.assertEqual(instance.log_path_in(game), game / "logs" / "latest.log")
        self.assertNotIn(str(self.checkout), str(instance.log_path_in(game)))

    def test_launch_gradle_runs_in_the_checkout_and_sets_java_home_only_when_asked(self):
        captured = {}

        def fake_popen(command, **kwargs):
            captured["command"] = list(command)
            captured["kwargs"] = kwargs
            return mock.Mock(pid=4242)

        with mock.patch.object(instance.subprocess, "Popen", side_effect=fake_popen):
            instance.launch_gradle(self.checkout, ":fabric:runClient", self.tmp / "game",
                                   world="jechworld", java_home=self.tmp / "jdk21",
                                   log_path=self.tmp / "gradle.log", log=lambda *_: None)
        self.assertEqual(captured["kwargs"]["cwd"], str(self.checkout))
        self.assertEqual(captured["kwargs"]["env"]["JAVA_HOME"], str(self.tmp / "jdk21"))
        self.assertTrue(str(captured["command"][0]).endswith("gradlew"))

        with mock.patch.object(instance.subprocess, "Popen", side_effect=fake_popen), \
                mock.patch.dict(os.environ, {"JAVA_HOME": "/ambient/jdk"}, clear=False):
            instance.launch_gradle(self.checkout, ":fabric:runClient", self.tmp / "game",
                                   log_path=self.tmp / "gradle.log", log=lambda *_: None)
        self.assertEqual(captured["kwargs"]["env"]["JAVA_HOME"], "/ambient/jdk",
                         "without daemon_java the ambient JAVA_HOME is left alone")

    def test_the_game_is_matched_on_its_working_directory(self):
        game_dir = "/runs/rid/game"
        table = {
            100: (1, "/bin/sh ./gradlew :fabric:runClient --no-daemon"),
            101: (100, "/opt/jdk21/bin/java org.gradle.wrapper.GradleWrapperMain :fabric:runClient"),
            102: (101, "/opt/jdk21/bin/java org.gradle.launcher.daemon.bootstrap.GradleDaemon 8.13"),
            103: (102, "/opt/jdk21/bin/java -Dfabric.dli.config=x net.fabricmc.devlaunchinjector.Main"),
            104: (102, "/opt/jdk21/bin/java -Xmx32m -cp . JavaProbe"),
        }
        cwds = {100: "/checkout", 101: "/checkout", 102: "/checkout", 103: game_dir,
                104: "/checkout"}
        with mock.patch.object(instance, "process_table", return_value=table), \
                mock.patch.object(instance, "cwd_of", side_effect=lambda pid, **kw: cwds[pid]):
            self.assertEqual(instance.gradle_game_pids(100, game_dir), [103])

    def test_a_gradle_toolchain_probe_is_never_mistaken_for_the_game(self):
        """The bug a name heuristic caused: Gradle's own probe looked like a game.

        The probe is a java descendant that is not a Gradle JVM by name, so
        "any java that is not Gradle" matched it. The runner then recorded "the
        game started", watched the probe vanish a moment later and ended the run
        as if the client had exited — before the client was even launched, and
        without ending the launcher it had started.
        """
        table = {
            100: (1, "./gradlew :neoforge:runClient --no-daemon"),
            101: (100, "/opt/jdk21/bin/java org.gradle.launcher.daemon.bootstrap.GradleDaemon"),
            102: (101, "/azul-1.8/bin/java -Xmx32m -cp . JavaProbe"),
        }
        # The probe runs in the module directory, like every other Gradle JVM.
        with mock.patch.object(instance, "process_table", return_value=table), \
                mock.patch.object(instance, "cwd_of", return_value="/checkout"):
            self.assertEqual(instance.gradle_game_pids(100, "/runs/rid/game"), [])

    def test_a_client_in_another_run_s_game_directory_is_not_this_run(self):
        """The identity is this run's own directory, not "some game directory"."""
        other = "/runs/other-rid/game"
        table = {100: (1, "./gradlew"), 103: (100, "/opt/jdk21/bin/java net.fabricmc.Main")}
        with mock.patch.object(instance, "process_table", return_value=table), \
                mock.patch.object(instance, "cwd_of", return_value=other):
            self.assertEqual(instance.gradle_game_pids(100, "/runs/this-rid/game"), [])

    def test_a_non_java_launcher_child_is_not_the_game(self):
        table = {100: (1, "./gradlew task"), 101: (100, "/bin/sh child-script")}
        with mock.patch.object(instance, "process_table", return_value=table), \
                mock.patch.object(instance, "cwd_of") as cwd:
            self.assertEqual(instance.gradle_game_pids(100, "/runs/rid/game"), [])
        cwd.assert_not_called()

    def test_cwd_of_reads_the_name_column(self):
        table = {4242: (1, "java -jar x.jar")}
        with mock.patch.object(instance, "process_table", return_value=table), \
                mock.patch.object(instance, "lsof_command", return_value="lsof"), \
                mock.patch.object(instance.subprocess, "run",
                                  return_value=mock.Mock(stdout=(
                                      "COMMAND  PID USER   FD   TYPE DEVICE SIZE/OFF   NODE NAME\n"
                                      "java    4242 user  cwd    DIR   1,14      384 65 /run dir/game\n"),
                                  )):
            self.assertEqual(instance.cwd_of(4242), "/run dir/game")

    def test_cwd_of_returns_none_for_a_gone_process(self):
        with mock.patch.object(instance, "lsof_command", return_value="lsof"), \
                mock.patch.object(instance.subprocess, "run",
                                  return_value=mock.Mock(stdout="")):
            self.assertIsNone(instance.cwd_of(4242))


class GradleGameTrackerTest(unittest.TestCase):
    """The search runs until the client is found, then only liveness is re-checked."""

    def test_the_search_stops_once_the_client_is_found_and_liveness_takes_over(self):
        clock = {"now": 0.0}
        alive = {"pids": []}

        def fake_search(launch_pid, game_dir, **kwargs):
            found = list(alive["pids"])
            return found

        with mock.patch.object(instance, "gradle_game_pids", side_effect=fake_search) as search, \
                mock.patch.object(instance, "pid_alive", side_effect=lambda pid: pid in alive["pids"]):
            tracker = instance.GradleGameTracker(100, "/runs/rid/game", interval=1.0,
                                                 clock=lambda: clock["now"])
            self.assertEqual(tracker.pids(), [], "nothing found on the first search")
            clock["now"] = 0.5
            self.assertEqual(tracker.pids(), [], "too soon to search again")
            self.assertEqual(search.call_count, 1)

            alive["pids"] = [999]
            clock["now"] = 2.0
            self.assertEqual(tracker.pids(), [999])
            self.assertEqual(search.call_count, 2)

            # Once found, the search is never repeated: liveness is enough.
            for step in range(1, 6):
                clock["now"] = 2.0 + step
                self.assertEqual(tracker.pids(), [999])
            self.assertEqual(search.call_count, 2)

            alive["pids"] = []
            self.assertEqual(tracker.pids(), [], "a dead client is reported as gone")
            self.assertEqual(search.call_count, 2, "and the search does not start over")


class ProcessHelpersTest(unittest.TestCase):
    def test_descendants_walks_the_tree(self):
        table = {1: (0, "root"), 2: (1, "child"), 3: (2, "grandchild"), 4: (0, "other")}
        self.assertEqual(instance.descendants([1], table), {1, 2, 3})

    def test_clear_dir_handles_a_file_in_place_of_a_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = paths.Path(tmp) / "logs"
            path.write_text("not a dir", encoding="utf-8")
            self.assertEqual(instance.clear_dir(path), 1)
            self.assertTrue(path.is_dir())
            self.assertEqual(list(path.iterdir()), [])

    def test_clear_dir_creates_a_missing_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = paths.Path(tmp) / "saves"
            self.assertEqual(instance.clear_dir(path), 0)
            self.assertTrue(path.is_dir())

    def test_is_new_session_rejects_the_previous_session_log(self):
        with tempfile.TemporaryDirectory() as tmp:
            log_path = paths.Path(tmp) / "latest.log"
            log_path.write_text("previous run\n", encoding="utf-8")
            os.utime(log_path, (1, 1))
            now = 1_800_000_000.0
            self.assertFalse(instance.is_new_session(log_path, now))
            os.utime(log_path, (now, now))
            self.assertTrue(instance.is_new_session(log_path, now))
            self.assertFalse(instance.is_new_session(log_path, now + 3600))

    def test_an_empty_fresh_log_is_not_yet_a_session(self):
        with tempfile.TemporaryDirectory() as tmp:
            log_path = paths.Path(tmp) / "latest.log"
            log_path.write_text("", encoding="utf-8")
            now = os.path.getmtime(log_path)
            self.assertFalse(instance.is_new_session(log_path, now))

    def test_a_missing_log_is_not_a_session(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertFalse(instance.is_new_session(paths.Path(tmp) / "latest.log", 0.0))

    def test_log_follower_reads_incrementally(self):
        with tempfile.TemporaryDirectory() as tmp:
            log_path = paths.Path(tmp) / "latest.log"
            log_path.write_text("seed\n", encoding="utf-8")
            started = os.path.getmtime(log_path)
            follower = instance.LogFollower(log_path, started, poll=0.01)
            self.assertTrue(follower.wait_for_session(time.time() + 5))
            follower.poll_once()  # the session's first lines
            with open(log_path, "a", encoding="utf-8") as handle:
                handle.write("line one\n")
            follower.poll_once()
            with open(log_path, "a", encoding="utf-8") as handle:
                handle.write("line two\n")
            follower.poll_once()
            self.assertEqual(follower.text, "seed\nline one\nline two\n")
            self.assertEqual(follower.tail(4), "two\n")

    def test_a_stale_fresh_looking_log_is_not_accepted_after_the_run_started(self):
        with tempfile.TemporaryDirectory() as tmp:
            log_path = paths.Path(tmp) / "latest.log"
            log_path.write_text("previous session\n", encoding="utf-8")
            started = os.path.getmtime(log_path) + 3600  # the run starts an hour later
            follower = instance.LogFollower(log_path, started, poll=0.01)
            self.assertFalse(follower.wait_for_session(time.time() + 0.05))
            self.assertFalse(follower.session_started)
            self.assertEqual(follower.poll_once(), "")


if __name__ == "__main__":
    unittest.main()
