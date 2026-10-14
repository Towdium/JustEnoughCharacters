"""The env schema: valid files load, every broken shape is rejected with detail."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from lib import envs, paths
from lib.errors import EnvSchemaError

from . import support


class EnvParsingTest(unittest.TestCase):
    def test_every_shipped_env_is_schema_valid(self):
        loaded = envs.load_all()
        self.assertGreaterEqual(len(loaded), 16, "the documented env matrix must ship")
        names = [env.name for env in loaded]
        self.assertEqual(len(names), len(set(names)), "env names must be unique")
        self.assertNotIn("example", names, "the schema example is replaced by the real matrix")
        for env in loaded:
            self.assertEqual(env.path.stem, env.name,
                             f"{env.path.name} must be named after its env")
            self.assertTrue(env.jech.source, env.name)
            self.assertTrue(env.checks, f"{env.name} must declare at least one check")
            for mod in env.mods:
                self.assertIn(mod.source, envs.MOD_SOURCES, env.name)
            if env.jech.source == "local-build":
                self.assertEqual(env.jech.expect_mod_id, "jecharacters", env.name)
                self.assertFalse(Path(env.jech.path).is_absolute(), env.name)

    def test_the_env_schema_is_documented_next_to_the_envs(self):
        schema = paths.envs_dir() / "README.md"
        self.assertTrue(schema.is_file(), "envs/README.md is the schema's human half")
        text = schema.read_text(encoding="utf-8")
        for check_type in envs.CHECK_TYPES:
            self.assertIn(check_type, text)

    def test_defaults_are_applied(self):
        env = support.mk_env(jvm=None, timeout_sec=None)
        self.assertEqual(env.timeout_sec, envs.DEFAULT_TIMEOUT_SEC)
        self.assertEqual(env.jvm.max_mem_mb, envs.DEFAULT_MAX_MEM_MB)
        self.assertEqual(env.jvm.min_mem_mb, envs.DEFAULT_MIN_MEM_MB)
        self.assertEqual(env.jvm.window, (1280, 720))

    def test_jvm_window_overrides(self):
        env = support.mk_env(jvm={"max_mem_mb": 2048, "min_mem_mb": 512, "window": [1024, 768]})
        self.assertEqual(env.jvm.window, (1024, 768))
        self.assertEqual(env.jvm.max_mem_mb, 2048)
        self.assertEqual(env.jvm.min_mem_mb, 512)

    def test_jvm_args_carry_only_the_extra_arguments_not_the_heap(self):
        """Heap lives in Prism's memory keys; -Xmx/-Xms there trigger a modal warning."""
        env = support.mk_env(jvm={"max_mem_mb": 2048, "min_mem_mb": 512,
                                  "args": ["-Dfoo=bar"]})
        self.assertEqual(env.jvm.jvm_args(), "-Dfoo=bar")
        self.assertNotIn("-Xmx", env.jvm.jvm_args())
        self.assertNotIn("-Xms", env.jvm.jvm_args())
        self.assertEqual(support.mk_env().jvm.jvm_args(), "")

    def test_comment_keys_are_allowed(self):
        data = support.env_dict()
        data["_comment"] = "explain"
        data["$schema"] = "ignore me"
        envs.parse_env(data, "<dict>")

    def test_load_all_and_find_env(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            (directory / "one.json").write_text(json.dumps(support.env_dict(name="one")), encoding="utf-8")
            (directory / "two.json").write_text(json.dumps(support.env_dict(name="two")), encoding="utf-8")
            loaded = envs.load_all(directory)
            self.assertEqual([env.name for env in loaded], ["one", "two"])
            self.assertEqual(envs.find_env("two", directory).name, "two")
            self.assertEqual(envs.find_env("one", directory).name, "one")
            with self.assertRaises(EnvSchemaError):
                envs.find_env("missing", directory)

    def test_write_round_trip_keeps_pins_and_comments(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "unit.json"
            data = support.env_dict()
            data["_comment"] = "keep me"
            path.write_text(json.dumps(data), encoding="utf-8")
            env = envs.load_env(path)
            env.mods[0].raw["pin"] = {"fileId": 42, "filename": "jei.jar", "downloadUrl": "https://x/jei.jar"}
            env.write()
            reloaded = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual(reloaded["_comment"], "keep me")
            self.assertEqual(reloaded["mods"][0]["pin"]["fileId"], 42)
            self.assertEqual(envs.load_env(path).mods[0].pin["filename"], "jei.jar")

    def test_check_names_default_from_value(self):
        env = support.mk_env(checks=[{"type": "log_contains", "value": "a-marker"}])
        self.assertEqual(env.checks[0].name, "log_contains:a-marker")
        env = support.mk_env(checks=[{"type": "no_crash_reports"}])
        self.assertEqual(env.checks[0].name, "no_crash_reports")

    def test_a_file_exists_check_takes_a_relative_path(self):
        env = support.mk_env(checks=[{"type": "file_exists", "value": "config/jecharacters.json",
                                      "name": "config-generated"}])
        self.assertEqual(env.checks[0].type, "file_exists")
        self.assertEqual(env.checks[0].value, "config/jecharacters.json")
        self.assertEqual(env.checks[0].name, "config-generated")
        listed = support.mk_env(checks=[{"type": "file_exists",
                                        "value": ["config/a.json", "config/b.json"]}])
        self.assertEqual(listed.checks[0].value, ["config/a.json", "config/b.json"])

    def test_ready_when_is_optional(self):
        self.assertIsNone(support.mk_env().ready_when)

    def test_ready_when_holds_one_log_line(self):
        env = support.mk_env(ready_when={"value": "Sound engine started"})
        self.assertIsNotNone(env.ready_when)
        self.assertEqual(env.ready_when.value, "Sound engine started")

    def test_skip_is_optional_and_carries_the_reason(self):
        self.assertIsNone(support.mk_env().skip)
        env = support.mk_env(skip="  needs a world load  ")
        self.assertEqual(env.skip, "needs a world load")

    def test_a_skipped_env_carries_the_derived_skip_tag(self):
        """So a selection can say 'only what runs' as --exclude tag:skip."""
        plain = support.mk_env()
        self.assertNotIn("skip", plain.tags)
        skipped = support.mk_env(tags=["smoke"], skip="why not")
        self.assertEqual(skipped.tags, ["smoke", "skip"])
        self.assertEqual(support.mk_env(skip="x", tags=["skip"]).tags, ["skip"],
                         "the tag is not added twice")

    def test_the_derived_tag_is_never_written_back_to_the_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "unit.json"
            path.write_text(json.dumps(support.env_dict(skip="why not")), encoding="utf-8")
            env = envs.load_env(path)
            self.assertIn("skip", env.tags)
            env.write()
            self.assertNotIn("skip", json.loads(path.read_text(encoding="utf-8"))["tags"])

    def test_world_is_optional_and_names_the_saves_folder(self):
        self.assertIsNone(support.mk_env().world)
        env = support.mk_env(world={"name": "jechworld"})
        self.assertEqual(env.world.name, "jechworld")
        self.assertEqual(env.world.saves_path, "saves/jechworld")

    def test_the_shipped_matrix_declares_the_world_and_the_skip(self):
        """The env files are the record of what is verified and what is not."""
        loaded = {env.name: env for env in envs.load_all()}
        for name in ("1.21.1-neoforge", "1.21.1-fabric"):
            self.assertEqual(loaded[name].world.name, "jechworld", name)
            self.assertIsNone(loaded[name].skip, name)
        for name in ("mv-1.19.2-forge", "mv-1.19.2-fabric"):
            self.assertIn("needs world load", loaded[name].skip, name)
            self.assertIsNone(loaded[name].world, name)
            self.assertTrue(loaded[name].checks, "a skipped env keeps its checks as documentation")

    def test_every_shipped_env_declares_a_stopping_rule(self):
        """No env that is actually executed may be left without ``ready_when``.

        A modded client that reaches the title screen never exits on its own, so
        an env without a stopping rule can only end in a watchdog kill (exit
        code 3) — the run is bounded by ``timeout_sec`` even when the failure it
        should report is already in the log. That makes a missing ``ready_when``
        a broken env, not a lenient one. A skipped env is the exception: it is
        never executed, so it needs no stopping rule, and its checks stay
        written down as documentation.
        """
        for env in envs.load_all():
            if env.skip:
                continue
            self.assertIsNotNone(env.ready_when, f"{env.name} declares no ready_when")

    def test_the_shipped_matrix_declares_a_world_only_where_it_was_measured(self):
        """``world`` is a premise about the marker, not a decoration.

        The rows launched into a level are exactly the ones whose JEI version
        defers its registration phase to level load and that can take a quick
        play argument (1.20+); the rest assert startup markers and must stay
        without one, so a row cannot silently acquire a level load it was not
        measured with.
        """
        measured = {
            "1.21.1-neoforge", "1.21.1-fabric",
            "moderner-1.21.10-neoforge", "moderner-1.21.10-fabric",
            "moderner-26.1.2-neoforge", "moderner-26.1.2-fabric",
            "mv-1.20.1-forge", "mv-1.20.1-fabric",
        }
        for env in envs.load_all():
            if env.name in measured:
                self.assertIsNotNone(env.world, env.name)
                self.assertEqual(env.world.name, "jechworld", env.name)
            else:
                self.assertIsNone(env.world, f"{env.name} declares a world it was not measured with")


class EnvRejectionTest(unittest.TestCase):
    def reject(self, data, *needles):
        with self.assertRaises(EnvSchemaError) as caught:
            envs.parse_env(data, "<unit>")
        message = str(caught.exception)
        for needle in needles:
            self.assertIn(needle, message, f"expected {needle!r} in:\n{message}")
        return message

    def test_missing_name(self):
        self.reject(support.env_dict(name=None), "missing required key 'name'")

    def test_name_must_be_filesystem_safe(self):
        self.reject(support.env_dict(name="a b/c"), "must match")

    def test_unknown_top_level_key(self):
        data = support.env_dict()
        data["timeout"] = 5
        self.reject(data, "unknown key 'timeout'")

    def test_bad_loader_type(self):
        self.reject(support.env_dict(loader={"type": "liteloader", "version": "1"}), "not one of")

    def test_loader_requires_a_version(self):
        self.reject(support.env_dict(loader={"type": "forge"}), "missing required key 'version'")

    def test_java_version_must_be_an_integer(self):
        self.reject(support.env_dict(java={"version": "21"}), "must be an integer")

    def test_timeout_must_be_a_realistic_integer(self):
        self.reject(support.env_dict(timeout_sec=5), "timeout_sec must be an integer")

    def test_local_build_requires_path_and_expected_mod_id(self):
        self.reject(
            support.env_dict(jech={"source": "local-build", "path": "build/libs/*.jar"}),
            "missing required key 'expectModId'",
        )

    def test_local_build_path_must_be_relative(self):
        self.reject(
            support.env_dict(jech={"source": "local-build", "path": "/abs/build/libs/*.jar",
                                   "expectModId": "jecharacters"}),
            "must be relative",
        )

    def test_remote_jech_requires_a_slug_and_a_version(self):
        self.reject(support.env_dict(jech={"source": "modrinth"}), "missing required key 'slug'")

    def test_curseforge_pin_requires_a_file_id(self):
        self.reject(
            support.env_dict(mods=[{"name": "jei", "source": "curseforge", "slug": "jei",
                                    "describe": {"version": "1"}, "pin": {"filename": "x.jar"}}]),
            "must carry 'fileId'",
        )

    def test_modrinth_pin_requires_a_version_id(self):
        self.reject(
            support.env_dict(mods=[{"name": "jei", "source": "modrinth", "slug": "jei",
                                    "describe": {"version": "1"}, "pin": {"fileId": 3}}]),
            "must carry 'versionId'",
        )

    def test_pin_id_must_not_be_empty(self):
        self.reject(
            support.env_dict(mods=[{"name": "jei", "source": "curseforge", "slug": "jei",
                                    "describe": {"version": "1"}, "pin": {"fileId": ""}}]),
            "must be a non-empty id",
        )

    def test_mod_needs_a_source_and_a_slug(self):
        self.reject(
            support.env_dict(mods=[{"name": "jei", "describe": {"version": "1"}}]),
            "missing required key 'source'",
            "missing required key 'slug'",
        )

    def test_duplicate_mod_names_are_rejected(self):
        mod = {"name": "jei", "source": "curseforge", "slug": "jei", "describe": {"version": "1"}}
        self.reject(support.env_dict(mods=[mod, dict(mod)]), "is a duplicate")

    def test_unknown_check_type(self):
        self.reject(support.env_dict(checks=[{"type": "log_maybe"}]),
                    "not one of log_contains")

    def test_check_value_is_required_for_log_checks(self):
        self.reject(support.env_dict(checks=[{"type": "log_contains"}]), "value is required")

    def test_file_exists_requires_a_relative_path(self):
        self.reject(support.env_dict(checks=[{"type": "file_exists"}]), "value is required")
        self.reject(support.env_dict(checks=[{"type": "file_exists", "value": "  "}]),
                    "must not be blank")
        self.reject(support.env_dict(checks=[{"type": "file_exists", "value": "/etc/passwd"}]),
                    "absolute path")
        self.reject(support.env_dict(checks=[{"type": "file_exists",
                                              "value": ["config/a.json", "/etc/passwd"]}]),
                    "absolute path")

    def test_value_is_rejected_on_value_less_checks(self):
        self.reject(support.env_dict(checks=[{"type": "no_crash_reports", "value": "x"}]),
                    "value is not allowed")

    def test_duplicate_check_names_are_rejected(self):
        self.reject(
            support.env_dict(checks=[{"type": "log_contains", "value": "a", "name": "same"},
                                     {"type": "log_not_contains", "value": "b", "name": "same"}]),
            "is a duplicate",
        )

    def test_ready_when_requires_a_value(self):
        self.reject(support.env_dict(ready_when={}), "missing required key 'value'")
        self.reject(support.env_dict(ready_when={"value": "  "}), "must be a non-empty string")

    def test_ready_when_rejects_unknown_keys_and_wrong_shapes(self):
        self.reject(support.env_dict(ready_when={"value": "x", "timeout": 1}), "unknown key 'timeout'")
        self.reject(support.env_dict(ready_when="Sound engine started"),
                    'env.ready_when must be an object')

    def test_skip_must_be_a_non_empty_string(self):
        self.reject(support.env_dict(skip="  "), "env.skip must be a non-empty string")
        self.reject(support.env_dict(skip=5), "env.skip must be a non-empty string")
        self.reject(support.env_dict(skip=["why"]), "env.skip must be a non-empty string")

    def test_world_requires_a_folder_name(self):
        self.reject(support.env_dict(world={}), "missing required key 'name'")
        self.reject(support.env_dict(world={"name": ""}), "must be a non-empty string")
        self.reject(support.env_dict(world="jechworld"), 'env.world must be an object')
        self.reject(support.env_dict(world={"name": "saves/jechworld"}),
                    "must be a plain folder name")
        self.reject(support.env_dict(world={"name": ".."}), "must be a plain folder name")
        self.reject(support.env_dict(world={"name": "jechworld", "seed": 1}),
                    "unknown key 'seed'")

    def test_all_problems_are_reported_together(self):
        message = self.reject(
            support.env_dict(mc=None, java={"version": "x"}, timeout_sec=1),
            "missing required key 'mc'",
            "env.java.version must be an integer",
            "timeout_sec",
        )
        self.assertIn("schema problem(s)", message)
        self.assertGreaterEqual(message.count("  - "), 3)


class GradleBlockTest(unittest.TestCase):
    """``env.gradle`` selects the launch layer; its absence keeps Prism."""

    def reject(self, data, *needles):
        with self.assertRaises(EnvSchemaError) as caught:
            envs.parse_env(data, "<unit>")
        message = str(caught.exception)
        for needle in needles:
            self.assertIn(needle, message, f"expected {needle!r} in:\n{message}")
        return message

    def test_an_env_without_the_block_is_the_prism_backend(self):
        env = support.mk_env()
        self.assertIsNone(env.gradle)
        self.assertEqual(env.backend, envs.PRISM_BACKEND)

    def test_the_block_switches_the_backend_and_keeps_its_fields(self):
        env = support.mk_env(gradle={"task": ":neoforge:runClient", "project_dir": "1.21.1",
                                     "daemon_java": 21})
        self.assertEqual(env.backend, envs.GRADLE_BACKEND)
        self.assertEqual(env.gradle.task, ":neoforge:runClient")
        self.assertEqual(env.gradle.project_dir, "1.21.1")
        self.assertEqual(env.gradle.daemon_java, 21)

    def test_project_dir_defaults_to_the_jech_project(self):
        """The task runs in the same checkout the artifact was resolved from."""
        env = support.mk_env(gradle={"task": ":fabric:runClient"})
        self.assertEqual(env.gradle.project_dir, "1.21.1")
        self.assertIsNone(env.gradle.daemon_java, "no daemon_java means the ambient JAVA_HOME")

    def test_the_task_is_required(self):
        self.reject(support.env_dict(gradle={"project_dir": "1.21.1"}),
                    "missing required key 'task'")

    def test_project_dir_may_not_be_absolute(self):
        self.reject(support.env_dict(gradle={"task": "runClient", "project_dir": "/tmp/x"}),
                    "must be relative")

    def test_an_unknown_key_in_the_block_is_rejected(self):
        self.reject(support.env_dict(gradle={"task": "runClient", "project_dir": "x", "java": 21}),
                    "unknown key 'java'")

    def test_daemon_java_must_be_a_plausible_major_version(self):
        self.reject(support.env_dict(gradle={"task": "runClient", "project_dir": "x",
                                            "daemon_java": "21"}),
                    "must be an integer")
        self.reject(support.env_dict(gradle={"task": "runClient", "project_dir": "x",
                                            "daemon_java": 4}),
                    "must be >= 8")

    def test_an_env_without_jech_project_still_needs_an_explicit_project_dir(self):
        data = support.env_dict(gradle={"task": "runClient"})
        data["jech"] = {"source": "curseforge", "slug": "jecharacters",
                        "describe": {"version": "1"}, "pin": {"fileId": 1, "filename": "j.jar"}}
        with self.assertRaises(EnvSchemaError) as caught:
            envs.parse_env(data, "<unit>")
        self.assertIn("project_dir is required", str(caught.exception))


class ViaTest(unittest.TestCase):
    """``via`` decides whether an artifact is staged into mods/ or rides the classpath."""

    def test_the_default_is_game_dir(self):
        env = support.mk_env()
        self.assertEqual(env.jech.via, envs.DEFAULT_VIA)
        self.assertEqual(env.mods[0].via, envs.DEFAULT_VIA)

    def test_dev_classpath_is_accepted_on_the_mod_under_test_and_on_mods(self):
        data = support.env_dict()
        data["jech"]["via"] = "dev-classpath"
        data["mods"][0]["via"] = "dev-classpath"
        env = envs.parse_env(data, "<unit>")
        self.assertEqual(env.jech.via, "dev-classpath")
        self.assertEqual(env.mods[0].via, "dev-classpath")

    def test_an_unknown_value_is_rejected_with_the_allowed_ones(self):
        for key in ("jech", "mods"):
            data = support.env_dict()
            if key == "jech":
                data["jech"]["via"] = "classpath"
            else:
                data["mods"][0]["via"] = "classpath"
            with self.assertRaises(EnvSchemaError) as caught:
                envs.parse_env(data, "<unit>")
            message = str(caught.exception)
            self.assertIn("via must be one of", message)
            self.assertIn("dev-classpath", message)

    def test_the_shipped_gradle_envs_stage_only_what_the_checkout_lacks(self):
        """The two 1.21.1 rows are the dev-classpath contract, written down.

        NeoForge's checkout provides JECh's sources *and* JEI through
        ``localRuntime``, so both ride the classpath; the Fabric checkout provides
        JECh's sources and ``modImplementation`` fabric-api, but nothing for JEI,
        so JEI's jar is the one jar staged into ``mods/``.
        """
        loaded = {env.name: env for env in envs.load_all()}
        neoforge = loaded["1.21.1-neoforge"]
        self.assertEqual(neoforge.backend, envs.GRADLE_BACKEND)
        self.assertEqual(neoforge.gradle.task, ":neoforge:runClient")
        self.assertEqual(neoforge.jech.via, "dev-classpath")
        self.assertEqual([mod.via for mod in neoforge.mods], ["dev-classpath"])

        fabric = loaded["1.21.1-fabric"]
        self.assertEqual(fabric.backend, envs.GRADLE_BACKEND)
        self.assertEqual(fabric.gradle.task, ":fabric:runClient")
        self.assertEqual(fabric.jech.via, "dev-classpath")
        self.assertEqual({mod.name: mod.via for mod in fabric.mods},
                         {"jei": "game-dir", "fabric-api": "dev-classpath"})

    def test_the_other_shipped_envs_keep_the_prism_backend(self):
        loaded = {env.name: env for env in envs.load_all()}
        prism_envs = [env for name, env in loaded.items()
                      if not name.startswith("1.21.1-")]
        self.assertTrue(prism_envs)
        for env in prism_envs:
            self.assertIsNone(env.gradle, env.name)
            self.assertEqual(env.backend, envs.PRISM_BACKEND, env.name)
            self.assertEqual(env.jech.via, envs.DEFAULT_VIA, env.name)
            for mod in env.mods:
                self.assertEqual(mod.via, envs.DEFAULT_VIA, f"{env.name}/{mod.name}")


class CheckoutWiringTest(unittest.TestCase):
    """The gradle contract: the harness's properties must be read by the checkout.

    Read-only and layout-tolerant: with the workstation layout (one checkout per
    version line under the JECh root) both build files are found and checked; in a
    CI checkout, where the branch root already *is* the version line, the module
    path does not resolve this way and the check is skipped rather than faked.
    """

    def build_gradle(self, env):
        from lib import resolve
        module = env.gradle.task.lstrip(":").split(":")[0]
        path = (resolve.jech_base_root() / env.gradle.project_dir / module / "build.gradle")
        return path

    def test_both_1_21_1_modules_read_the_harness_properties(self):
        from lib import instance
        loaded = {env.name: env for env in envs.load_all()}
        checked = []
        for name in ("1.21.1-neoforge", "1.21.1-fabric"):
            env = loaded[name]
            path = self.build_gradle(env)
            if not path.is_file():
                continue
            text = path.read_text(encoding="utf-8")
            checked.append(name)
            for needle in (instance.GRADLE_RUN_DIR_PROPERTY,
                           instance.GRADLE_QUICK_PLAY_PROPERTY):
                self.assertIn(needle, text, f"{name}: {path} does not read {needle!r}")
            # The wiring must be conditional: a dev run with neither property must
            # keep the checkout's own defaults.
            self.assertIn("isPresent()", text, f"{name}: the wiring is not property-guarded")
        self.assertTrue(checked, "no checkout found; the wiring contract was not verified")

    def test_the_fabric_module_puts_the_generated_targets_on_the_dev_classpath(self):
        """A dev run must carry ``targets.json`` even though only the jar gets it.

        The jech plugin wires the generated transformer targets into the jar task
        alone, so ``runClient`` (which resolves the module's own resources) has no
        ``me/towdium/jecharacters/targets.json``, the mixin plugin throws
        "Could not find targets.json", and JECh's class transformer silently never
        loads — a real JECh behaviour this harness exists to catch, so the checkout
        has to close it for a dev run as well.
        """
        from lib import resolve
        loaded = {env.name: env for env in envs.load_all()}
        env = loaded["1.21.1-fabric"]
        path = (resolve.jech_base_root() / env.gradle.project_dir / "fabric" / "build.gradle")
        if not path.is_file():
            self.skipTest(f"{path} not present in this layout")
        text = path.read_text(encoding="utf-8")
        self.assertIn("generateTargetConfig", text,
                      "the dev run must carry the generated transformer targets")
        self.assertIn("processResources", text,
                      "the generated targets must reach the module's resource output")
        self.assertIn("duplicatesStrategy", text,
                      "the jar task fails on the now-duplicated generated file")


if __name__ == "__main__":
    unittest.main()
