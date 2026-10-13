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
        self.assertEqual(values["OverrideMemory"], "true")
        # heap is not repeated in JvmArgs: Prism warns modally when -Xmx/-Xms appear there
        self.assertEqual(values["JvmArgs"], "")
        self.assertNotIn("-Xmx", values["JvmArgs"])
        self.assertEqual((values["MinecraftWinWidth"], values["MinecraftWinHeight"]), ("1280", "720"))

    def test_extra_jvm_args_reach_jvmargs(self):
        env = support.mk_env(jvm={"max_mem_mb": 3072, "args": ["-Dfoo=bar"]})
        values = instance.cfg_values_for(env, java_path="/jdk/bin/java")
        self.assertEqual(values["JvmArgs"], "-Dfoo=bar")
        self.assertEqual(values["MaxMemAlloc"], "3072")


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

    @staticmethod
    def fake_materialize():
        """A stand-in for :func:`instance.materialize_instance` (no JDK needed)."""
        counter = {"n": 0}

        def run(_env, instance_id, *, root=None, log=print, **kwargs):
            counter["n"] += 1
            support.instance_dir_with(paths.prism_root(), instance_id)
            return instance_id

        run.calls = counter
        return run

    def test_first_run_creates_and_later_runs_reuse(self):
        env = support.mk_env()
        jar = support.write_jar(self.tmp / "jei.jar", mod_id="jei")
        resolved = support.resolved_env(env, [support.artifact("jei", jar)])
        packs = self.make_pack(env, resolved)
        fake_materialize = self.fake_materialize()

        with mock.patch.object(instance, "materialize_instance", side_effect=fake_materialize), \
                mock.patch.object(instance, "configure_instance", return_value={}) as configure:
            first = instance.ensure_instance(env, resolved, packs, log=lambda *_: None)
            self.assertTrue(first.imported)
            self.assertFalse(first.reused)
            second = instance.ensure_instance(env, resolved, packs, log=lambda *_: None)
            self.assertTrue(second.reused)
            self.assertEqual(second.instance_id, first.instance_id)

        self.assertEqual(fake_materialize.calls["n"], 1)
        # creation writes instance.cfg itself; the reuse path re-applies it, because
        # the env file is the authority for java/memory/window and may change
        # without the modset (which is what keys the instance) changing.
        self.assertEqual(configure.call_count, 1)
        self.assertEqual(first.instance_id, instance.instance_id_for(env, first.modset_hash))
        state = json.loads(paths.state_path().read_text(encoding="utf-8"))
        self.assertEqual(state["envs"][env.name]["instance_id"], first.instance_id)
        self.assertEqual(state["envs"][env.name]["modset_hash"], first.modset_hash)

    def test_a_changed_modset_materialises_a_fresh_instance(self):
        env = support.mk_env()
        jar_a = support.write_jar(self.tmp / "a" / "jei.jar", mod_id="jei")
        jar_b = support.write_jar(self.tmp / "b" / "jei.jar", mod_id="jei", payload="changed\n")
        fake_materialize = self.fake_materialize()

        with mock.patch.object(instance, "materialize_instance", side_effect=fake_materialize), \
                mock.patch.object(instance, "configure_instance", return_value={}):
            first = instance.ensure_instance(env, support.resolved_env(env, [support.artifact("jei", jar_a)]),
                                             self.make_pack(env, None), log=lambda *_: None)
            second = instance.ensure_instance(env, support.resolved_env(env, [support.artifact("jei", jar_b)]),
                                              self.make_pack(env, None), log=lambda *_: None)
        self.assertNotEqual(first.instance_id, second.instance_id)
        self.assertEqual(fake_materialize.calls["n"], 2)
        self.assertFalse(second.reused)

    def test_the_instance_id_is_deterministic_for_env_and_modset(self):
        env = support.mk_env()
        jar = support.write_jar(self.tmp / "jei.jar", mod_id="jei")
        resolved = support.resolved_env(env, [support.artifact("jei", jar)])
        digest = pack.modset_hash(env, resolved)
        self.assertEqual(instance.instance_id_for(env, digest), instance.instance_id_for(env, digest))
        self.assertTrue(instance.instance_id_for(env, digest).startswith(env.name + "-"))
        self.assertIn(digest[:12], instance.instance_id_for(env, digest))
        other = support.mk_env(name="other-env")
        self.assertNotEqual(instance.instance_id_for(env, digest), instance.instance_id_for(other, digest))

    def test_a_leftover_directory_is_cleared_before_the_new_instance(self):
        """A directory state.json does not point at is debris, not a reusable instance."""
        env = support.mk_env()
        jar = support.write_jar(self.tmp / "jei.jar", mod_id="jei")
        resolved = support.resolved_env(env, [support.artifact("jei", jar)])
        desired = instance.instance_id_for(env, pack.modset_hash(env, resolved))
        leftover = support.instance_dir_with(paths.prism_root(), desired)
        (leftover / "minecraft" / "mods").mkdir(parents=True, exist_ok=True)
        (leftover / "minecraft" / "mods" / "stale.jar").write_text("old", encoding="utf-8")
        self.assertTrue(leftover.exists())

        seen = {}

        def fake_materialize(_env, instance_id, *, root=None, log=print, **kwargs):
            seen["leftover_still_there"] = leftover.exists()
            support.instance_dir_with(paths.prism_root(), instance_id)
            return instance_id

        with mock.patch.object(instance, "materialize_instance", side_effect=fake_materialize), \
                mock.patch.object(instance, "configure_instance", return_value={}):
            info = instance.ensure_instance(env, resolved, self.make_pack(env, resolved),
                                            log=lambda *_: None)
        self.assertFalse(seen["leftover_still_there"], "the debris must be removed first")
        self.assertTrue(info.imported)
        self.assertEqual(info.instance_id, desired)
        self.assertFalse((leftover / "minecraft" / "mods" / "stale.jar").exists())

    def test_a_recorded_instance_missing_on_disk_is_recreated(self):
        env = support.mk_env()
        jar = support.write_jar(self.tmp / "jei.jar", mod_id="jei")
        resolved = support.resolved_env(env, [support.artifact("jei", jar)])
        digest = pack.modset_hash(env, resolved)
        desired = instance.instance_id_for(env, digest)
        instance.write_state({"version": 1, "envs": {env.name: {
            "instance_id": desired, "modset_hash": digest}}})
        with mock.patch.object(instance, "materialize_instance",
                               side_effect=self.fake_materialize()) as create_mock, \
                mock.patch.object(instance, "configure_instance", return_value={}):
            info = instance.ensure_instance(env, resolved, self.make_pack(env, resolved), log=lambda *_: None)
        self.assertTrue(info.imported)
        self.assertEqual(info.instance_id, desired)
        self.assertEqual(create_mock.call_count, 1)


class MaterializeTest(unittest.TestCase):
    """The instance is written directly; Prism is only asked to launch it."""

    def setUp(self):
        self._home = support.temp_home()
        self.home = self._home.__enter__()
        self.addCleanup(self._home.__exit__, None, None, None)
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.root = paths.Path(self._tmp.name) / "prism"
        self.jdk = paths.Path(self._tmp.name) / "jdk-21"
        (self.jdk / "bin").mkdir(parents=True)
        (self.jdk / "bin" / "java").write_text("#!/bin/sh\n", encoding="utf-8")

    def materialize(self, **overrides):
        env = support.mk_env(**overrides)
        instance_id = instance.instance_id_for(env, "deadbeefcafe")
        instance.materialize_instance(env, instance_id, root=self.root, java_home=self.jdk,
                                      log=lambda *_: None)
        return env, instance.instance_dir(instance_id, self.root)

    def test_it_writes_the_two_files_prism_reads(self):
        _env, directory = self.materialize()
        self.assertTrue((directory / "instance.cfg").is_file())
        self.assertTrue((directory / "mmc-pack.json").is_file())
        self.assertTrue((directory / "minecraft" / "mods").is_dir())

    def test_the_cfg_carries_the_envs_java_memory_and_window(self):
        _env, directory = self.materialize(jvm={"max_mem_mb": 2048, "min_mem_mb": 512,
                                                "window": [1024, 768]})
        cfg = instance.parse_cfg((directory / "instance.cfg").read_text(encoding="utf-8"))
        self.assertEqual(cfg["InstanceType"], "OneSix")
        self.assertEqual(cfg["MaxMemAlloc"], "2048")
        self.assertEqual(cfg["MinMemAlloc"], "512")
        self.assertEqual(cfg["OverrideJavaLocation"], "true")
        self.assertEqual(cfg["OverrideJavaArgs"], "true")
        self.assertEqual(cfg["OverrideWindow"], "true")
        self.assertEqual(cfg["JavaPath"], str(self.jdk / "bin" / "java"))
        self.assertEqual((cfg["MinecraftWinWidth"], cfg["MinecraftWinHeight"]), ("1024", "768"))
        self.assertEqual(cfg["name"], instance.instance_id_for(_env, "deadbeefcafe"))

    def test_the_pack_names_the_minecraft_and_loader_components(self):
        env, directory = self.materialize()
        packed = json.loads((directory / "mmc-pack.json").read_text(encoding="utf-8"))
        self.assertEqual(packed["formatVersion"], 1)
        by_uid = {c["uid"]: c for c in packed["components"]}
        self.assertEqual(by_uid["net.minecraft"]["version"], env.mc)
        self.assertEqual(by_uid["net.neoforged"]["version"], env.loader.version)
        self.assertTrue(by_uid["net.minecraft"]["important"])
        for component in packed["components"]:
            self.assertIn("cachedName", component)
            self.assertIn("cachedVersion", component)

    def test_every_loader_type_maps_to_a_component_uid(self):
        expected = {"neoforge": "net.neoforged", "forge": "net.minecraftforge",
                    "fabric": "net.fabricmc.fabric-loader", "quilt": "org.quiltmc.quilt-loader"}
        self.assertEqual(instance.LOADER_COMPONENT_UIDS, expected)
        for loader, uid in expected.items():
            env = support.mk_env(loader={"type": loader, "version": "1.2.3"})
            packed = instance.pack_json(env)
            self.assertIn(uid, [c["uid"] for c in packed["components"]], loader)

    def test_legacy_forge_component_drops_the_minecraft_prefix(self):
        """The mrpack dependency keeps ``1.12.2-...``; the component is bare, or it 404s."""
        env = support.mk_env(mc="1.12.2", loader={"type": "forge", "version": "1.12.2-14.23.5.2864"},
                             java={"version": 8})
        components = {c["uid"]: c["version"] for c in instance.pack_json(env)["components"]}
        self.assertEqual(components["net.minecraftforge"], "14.23.5.2864")
        # the mrpack dependency string is untouched: it needs the prefix
        self.assertEqual(pack.dependencies_for(env)["forge"], "1.12.2-14.23.5.2864")

    def test_a_forge_version_without_the_prefix_is_left_alone(self):
        env = support.mk_env(mc="1.20.1", loader={"type": "forge", "version": "47.0.35"})
        packed = {c["uid"]: c["version"] for c in instance.pack_json(env)["components"]}
        self.assertEqual(packed["net.minecraftforge"], "47.0.35")

    def test_a_missing_jdk_is_an_error_not_a_launch_failure(self):
        env = support.mk_env()
        with self.assertRaises(ConfigError):
            instance.materialize_instance(env, "unit", root=self.root,
                                          java_home=self.root / "no-such-jdk", log=lambda *_: None)

    def test_an_existing_directory_is_refused(self):
        env, directory = self.materialize()
        self.assertTrue(directory.is_dir())
        with self.assertRaises(ConfigError):
            instance.materialize_instance(env, directory.name, root=self.root,
                                          java_home=self.jdk, log=lambda *_: None)


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

    def test_game_pids_matches_the_instance_directory_not_the_game_directory(self):
        """Prism's JVM carries the instance dir (natives/library path), not ``.../minecraft``."""
        with tempfile.TemporaryDirectory() as tmp:
            root = paths.Path(tmp)
            support.instance_dir_with(root, "unit-instance", cfg=False)
            game = root / "instances" / "unit-instance" / "minecraft"
            table = {
                100: (1, f'/usr/bin/java -Djava.library.path={root}/instances/unit-instance/natives '
                         f'-cp {root}/libraries/minecraft.jar org.prismlauncher.EntryPoint'),
                101: (1, f'/usr/bin/java --gameDir {game} net.minecraft.client.main.Main'),
                102: (1, f'/usr/bin/java -jar {root}/instances/other-instance/minecraft/x.jar Main'),
                103: (1, f'/bin/sh -c tail -f {root}/instances/unit-instance/minecraft/logs/latest.log'),
            }
            with mock.patch.object(instance, "process_table", return_value=table):
                found = instance.game_pids("unit-instance", root=root)
        self.assertIn(100, found, "the natives/library path names the instance directory")
        self.assertIn(101, found, "a --gameDir command line is also a match")
        self.assertNotIn(102, found, "another instance must not match")
        self.assertNotIn(103, found, "a non-java process must not match")

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
