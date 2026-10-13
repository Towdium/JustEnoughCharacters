"""Prism instance handling: cfg editing, java lookup, reuse, isolation."""

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


class CfgTest(unittest.TestCase):
    ORIGINAL = (
        "InstanceType=OneSix\n"
        "iconKey=default\n"
        "name=1.21.1-neoforge\n"
        "JvmArgs=-Xmx512m\n"
        "OverrideJavaArgs=false\n"
        "# a comment\n"
    )

    def test_existing_keys_are_replaced_and_missing_ones_appended(self):
        updated = instance.set_cfg_values(self.ORIGINAL, {"JvmArgs": "-Xmx3072m", "JavaPath": "java-home/bin/java"})
        self.assertIn("JvmArgs=-Xmx3072m\n", updated)
        self.assertNotIn("-Xmx512m", updated)
        self.assertIn("JavaPath=java-home/bin/java\n", updated)
        self.assertIn("name=1.21.1-neoforge\n", updated)  # untouched keys survive
        self.assertIn("# a comment\n", updated)
        self.assertTrue(updated.endswith("\n"))

    def test_existing_quotes_are_dropped(self):
        updated = instance.set_cfg_values('JvmArgs="-Xmx2048m"\n', {"JvmArgs": "-Xms1024m -Xmx2048m"})
        self.assertEqual(updated, "JvmArgs=-Xms1024m -Xmx2048m\n")

    def test_no_duplicate_keys_are_created(self):
        updated = instance.set_cfg_values(self.ORIGINAL, {"JvmArgs": "-Xmx1m"})
        self.assertEqual(updated.count("JvmArgs="), 1)

    def test_parsing_reads_raw_values(self):
        parsed = instance.parse_cfg(self.ORIGINAL)
        self.assertEqual(parsed["name"], "1.21.1-neoforge")
        self.assertEqual(parsed["OverrideJavaArgs"], "false")
        self.assertNotIn("#", parsed)

    def test_cfg_values_for_an_env(self):
        env = support.mk_env(jvm={"max_mem_mb": 3072, "min_mem_mb": 1024, "window": [1280, 720]})
        values = instance.cfg_values_for(env, java_path="/jdk/bin/java")
        self.assertEqual(values["MaxMemAlloc"], "3072")
        self.assertEqual(values["MinMemAlloc"], "1024")
        self.assertEqual(values["JavaPath"], "/jdk/bin/java")
        self.assertEqual(values["OverrideJavaLocation"], "true")
        self.assertEqual(values["OverrideJavaArgs"], "true")
        self.assertEqual(values["OverrideWindow"], "true")
        self.assertEqual(values["JvmArgs"], "-Xms1024m -Xmx3072m")
        self.assertEqual((values["MinecraftWinWidth"], values["MinecraftWinHeight"]), ("1280", "720"))


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


class ReuseTest(unittest.TestCase):
    def setUp(self):
        self._home = support.temp_home()
        self.home = self._home.__enter__()
        self.addCleanup(self._home.__exit__, None, None, None)
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = paths.Path(self._tmp.name)

    def make_pack(self, env, resolved):
        return pack.PackResult(
            pack_dir=self.tmp / "pack", index_path=self.tmp / "pack" / "modrinth.json",
            mrpack_path=self.tmp / "unit.mrpack", pack_hash="deadbeef",
            mod_filenames=["jei.jar"], version_id=env.name, dependencies={},
        )

    def test_first_run_imports_and_later_runs_reuse(self):
        env = support.mk_env()
        jar = support.write_jar(self.tmp / "jei.jar", mod_id="jei")
        resolved = support.resolved_env(env, [support.artifact("jei", jar)])
        packs = self.make_pack(env, resolved)
        calls = {"import": 0}

        def fake_import(_mrpack, *, root=None, log=print, **kwargs):
            calls["import"] += 1
            instance_id = f"imported-{calls['import']}"
            support.instance_dir_with(paths.prism_root(), instance_id)
            return instance_id

        with mock.patch.object(instance, "import_pack", side_effect=fake_import), \
                mock.patch.object(instance, "configure_instance", return_value={}):
            first = instance.ensure_instance(env, resolved, packs, log=lambda *_: None)
            self.assertTrue(first.imported)
            self.assertFalse(first.reused)
            second = instance.ensure_instance(env, resolved, packs, log=lambda *_: None)
            self.assertTrue(second.reused)
            self.assertEqual(second.instance_id, first.instance_id)

        self.assertEqual(calls["import"], 1)
        state = json.loads(paths.state_path().read_text(encoding="utf-8"))
        self.assertEqual(state["envs"][env.name]["instance_id"], first.instance_id)
        self.assertEqual(state["envs"][env.name]["modset_hash"], first.modset_hash)

    def test_a_changed_modset_imports_a_fresh_instance(self):
        env = support.mk_env()
        jar_a = support.write_jar(self.tmp / "a" / "jei.jar", mod_id="jei")
        jar_b = support.write_jar(self.tmp / "b" / "jei.jar", mod_id="jei", payload="changed\n")
        counter = {"n": 0}

        def fake_import(_mrpack, *, root=None, log=print, **kwargs):
            counter["n"] += 1
            instance_id = f"imported-{counter['n']}"
            support.instance_dir_with(paths.prism_root(), instance_id)
            return instance_id

        with mock.patch.object(instance, "import_pack", side_effect=fake_import), \
                mock.patch.object(instance, "configure_instance", return_value={}):
            first = instance.ensure_instance(env, support.resolved_env(env, [support.artifact("jei", jar_a)]),
                                             self.make_pack(env, None), log=lambda *_: None)
            second = instance.ensure_instance(env, support.resolved_env(env, [support.artifact("jei", jar_b)]),
                                              self.make_pack(env, None), log=lambda *_: None)
        self.assertNotEqual(first.instance_id, second.instance_id)
        self.assertEqual(counter["n"], 2)
        self.assertFalse(second.reused)

    def test_a_recorded_instance_missing_on_disk_is_reimported(self):
        env = support.mk_env()
        jar = support.write_jar(self.tmp / "jei.jar", mod_id="jei")
        resolved = support.resolved_env(env, [support.artifact("jei", jar)])
        instance.write_state({"version": 1, "envs": {env.name: {
            "instance_id": "gone", "modset_hash": pack.modset_hash(env, resolved)}}})
        with mock.patch.object(instance, "import_pack", return_value="fresh") as import_mock, \
                mock.patch.object(instance, "configure_instance", return_value={}):
            support.instance_dir_with(paths.prism_root(), "fresh")
            info = instance.ensure_instance(env, resolved, self.make_pack(env, resolved), log=lambda *_: None)
        self.assertTrue(info.imported)
        self.assertEqual(info.instance_id, "fresh")
        self.assertEqual(import_mock.call_count, 1)


class IsolationTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = paths.Path(self._tmp.name)
        self.root = self.tmp / "prism"
        self.instance_id = "unit-instance"
        self.directory = support.instance_dir_with(self.root, self.instance_id)
        self.minecraft = self.directory / "minecraft"
        (self.minecraft / "mods").mkdir(parents=True)
        (self.minecraft / "config").mkdir(parents=True)
        (self.minecraft / "saves").mkdir(parents=True)
        (self.minecraft / "logs").mkdir(parents=True)
        (self.minecraft / "mods" / "stale.jar").write_text("old", encoding="utf-8")
        (self.minecraft / "mods" / "readme.txt").write_text("old", encoding="utf-8")
        (self.minecraft / "config" / "jecharacters.toml").write_text("pinyin=true\n", encoding="utf-8")
        (self.minecraft / "saves" / "world").mkdir()
        (self.minecraft / "logs" / "latest.log").write_text("previous session\n", encoding="utf-8")
        self.run_dir = self.tmp / "runs" / "unit"
        self.jar = support.write_jar(self.tmp / "jei.jar", mod_id="jei")
        self.resolved = support.resolved_env(support.mk_env(), [support.artifact("jei", self.jar)])

    def test_mods_are_resynced_from_scratch(self):
        report = instance.isolate(self.instance_id, self.resolved, self.run_dir,
                                  root=self.root, log=lambda *_: None)
        present = sorted(p.name for p in (self.minecraft / "mods").iterdir())
        self.assertEqual(present, ["jei.jar"])
        self.assertEqual(report.removed, 2)
        self.assertEqual(report.staged, ["jei.jar"])

    def test_config_is_archived_then_emptied(self):
        report = instance.isolate(self.instance_id, self.resolved, self.run_dir,
                                  root=self.root, log=lambda *_: None)
        self.assertEqual(report.config_archived, 1)
        self.assertEqual(list((self.minecraft / "config").iterdir()), [])
        archived = self.run_dir / "pre-run-config" / "jecharacters.toml"
        self.assertTrue(archived.is_file())
        self.assertEqual(archived.read_text(encoding="utf-8"), "pinyin=true\n")

    def test_saves_and_logs_are_emptied(self):
        instance.isolate(self.instance_id, self.resolved, self.run_dir, root=self.root,
                         log=lambda *_: None)
        self.assertEqual(list((self.minecraft / "saves").iterdir()), [])
        self.assertEqual(list((self.minecraft / "logs").iterdir()), [])

    def test_the_isolation_report_serialises(self):
        report = instance.isolate(self.instance_id, self.resolved, self.run_dir, root=self.root,
                                  log=lambda *_: None)
        payload = report.to_json()
        self.assertEqual(payload["modsStaged"], ["jei.jar"])
        self.assertEqual(payload["savesCleared"], 1)
        self.assertIn("mods:", report.summary())


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

    def test_list_instances_ignores_directories_without_instance_cfg(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = paths.Path(tmp)
            support.instance_dir_with(root, "real")
            (root / "instances" / "not-an-instance").mkdir(parents=True)
            (root / "instances" / ".hidden").mkdir(parents=True)
            self.assertEqual(sorted(instance.list_instances(root)), ["real"])

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
