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
        self.assertIn("-Xmx2048m", env.jvm.jvm_args())
        self.assertIn("-Xms512m", env.jvm.jvm_args())

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

    def test_all_problems_are_reported_together(self):
        message = self.reject(
            support.env_dict(mc=None, java={"version": "x"}, timeout_sec=1),
            "missing required key 'mc'",
            "env.java.version must be an integer",
            "timeout_sec",
        )
        self.assertIn("schema problem(s)", message)
        self.assertGreaterEqual(message.count("  - "), 3)


if __name__ == "__main__":
    unittest.main()
