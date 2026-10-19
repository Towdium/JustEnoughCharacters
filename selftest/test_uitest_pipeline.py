"""The standalone uitest backend's own contract tests.

Covers what the 1.21.1 move (2026-10-15) added to the schema and the pipeline:
the ``uitest.project`` / ``uitest.options`` keys, the three-state launch rule
(gradle / standalone / skip), the all-dev-classpath rule for standalone rows,
and the pure helpers of ``lib.uitest_pipeline`` — the run-task derivation and
the jar arguments, which W2's ported lines will exercise the same way.
"""

import unittest

from lib import envs, options, uitest_pipeline
from lib.errors import ConfigError, EnvSchemaError
from selftest import support


def standalone_dict(**overrides):
    """A minimal, valid standalone-uitest env (the 1.21.1-neoforge shape)."""
    data = support.env_dict()
    data.pop("gradle", None)
    data["uitest"] = {"selection": "tag:smoke,tag:jei",
                      "project": "uitest/1.21.1-neoforge"}
    data["jech"]["via"] = "dev-classpath"
    data["jech"]["path"] = "neoforge/build/libs/jecharacters-1.21.1-neoforge-*.jar"
    data["mods"][0]["via"] = "dev-classpath"
    data.update(overrides)
    return data


class StandaloneSchemaTest(unittest.TestCase):
    def parse(self, data):
        return envs.parse_env(data, "<unit>")

    def test_a_standalone_env_needs_no_gradle_block(self):
        env = self.parse(standalone_dict())
        self.assertEqual(env.uitest.project, "uitest/1.21.1-neoforge")
        self.assertIsNone(env.gradle)
        self.assertIsNone(env.skip)

    def test_project_and_gradle_are_mutually_exclusive(self):
        data = standalone_dict()
        data["gradle"] = {"task": ":neoforge:runClient"}
        with self.assertRaises(EnvSchemaError) as caught:
            self.parse(data)
        self.assertIn("mutually exclusive", str(caught.exception))

    def test_neither_gradle_nor_project_nor_skip_is_an_error(self):
        data = standalone_dict()
        data["uitest"] = {"selection": "tag:smoke"}
        with self.assertRaises(EnvSchemaError) as caught:
            self.parse(data)
        self.assertIn("env must declare a launch layer", str(caught.exception))

    def test_a_checkout_backend_uitest_still_needs_gradle(self):
        data = standalone_dict()
        data["uitest"] = {"selection": "tag:smoke"}
        data["gradle"] = {"task": ":neoforge:runClientTests"}
        env = self.parse(data)
        self.assertIsNone(env.uitest.project)
        self.assertEqual(env.gradle.task, ":neoforge:runClientTests")

    def test_project_must_be_a_plain_relative_path(self):
        for bad in ("/abs/uitest/x", "../uitest/x", "uitest/../x", "~/uitest/x"):
            data = standalone_dict()
            data["uitest"]["project"] = bad
            with self.assertRaises(EnvSchemaError, msg=bad):
                self.parse(data)

    def test_standalone_rows_stage_nothing(self):
        for who, mutate in (
            ("jech", lambda d: d["jech"].update(via="game-dir")),
            ("a mod", lambda d: d["mods"][0].update(via="game-dir")),
        ):
            data = standalone_dict()
            mutate(data)
            with self.assertRaises(EnvSchemaError, msg=who) as caught:
                self.parse(data)
            self.assertIn("stages nothing", str(caught.exception))


class StandaloneOptionsTest(unittest.TestCase):
    def parse(self, data):
        return envs.parse_env(data, "<unit>")

    def test_extra_options_round_trip(self):
        data = standalone_dict()
        data["uitest"]["options"] = {"version": "3955",
                                     "key_key.jei.focusSearch": "key.keyboard.f"}
        env = self.parse(data)
        self.assertEqual(env.uitest.options,
                         {"version": "3955", "key_key.jei.focusSearch": "key.keyboard.f"})

    def test_a_key_binding_without_a_version_is_rejected(self):
        data = standalone_dict()
        data["uitest"]["options"] = {"key_key.jei.focusSearch": "key.keyboard.f"}
        with self.assertRaises(EnvSchemaError) as caught:
            self.parse(data)
        self.assertIn("no version", str(caught.exception))

    def test_render_puts_the_version_line_first(self):
        text = options.render("1.21.1", extra={"key_key.jei.focusSearch": "key.keyboard.f",
                                               "version": "3955"})
        lines = text.strip().splitlines()
        self.assertEqual(lines[0], "version:3955")
        self.assertIn("key_key.jei.focusSearch:key.keyboard.f", lines)
        self.assertIn("lang:zh_cn", lines)
        self.assertIn("onboardAccessibility:true", lines)

    def test_render_without_extra_is_the_base_preset(self):
        self.assertEqual(options.render("1.21.1"), options.render("1.21.1", extra=None))


class PipelineHelpersTest(unittest.TestCase):
    def env(self, loader="neoforge", jech_path="neoforge/build/libs/jecharacters-1.21.1-neoforge-*.jar"):
        data = standalone_dict()
        data["loader"] = {"type": loader, "version": "1"}
        data["jech"]["path"] = jech_path
        return envs.parse_env(data, "<unit>")

    def test_the_run_task_follows_the_loader(self):
        self.assertEqual(uitest_pipeline.run_task_of(self.env("neoforge")), "runClientTests")
        self.assertEqual(uitest_pipeline.run_task_of(self.env("fabric")), "runClient")
        self.assertEqual(uitest_pipeline.run_task_of(self.env("forge")), "runClient")

    def test_the_jech_build_task_comes_from_the_glob(self):
        self.assertEqual(uitest_pipeline._jech_build_task(self.env()), ":neoforge:build")
        self.assertEqual(
            uitest_pipeline._jech_build_task(
                self.env(jech_path="fabric/build/libs/jecharacters-1.21.1-fabric-*.jar")),
            ":fabric:build")

    def test_a_path_outside_build_libs_is_rejected(self):
        with self.assertRaises(ConfigError):
            uitest_pipeline._jech_build_task(
                self.env(jech_path="neoforge/libs/jecharacters.jar"))

    def test_jar_arguments_name_the_jei_mod(self):
        from lib.resolve import ResolvedArtifact, ResolvedEnv
        from pathlib import Path

        def artifact(name, path):
            return ResolvedArtifact(role="mod", name=name, source="curseforge",
                                    filename=f"{name}.jar", source_path=None, path=Path(path),
                                    sha1="x", size=1, detail="", via="dev-classpath")
        resolved = ResolvedEnv(env=None, artifacts=[
            artifact("fabric-api", "/cache/fabric-api.jar"), artifact("jei", "/cache/jei.jar")])
        args = uitest_pipeline.jar_arguments(Path("/checkouts/jech.jar"), resolved)
        self.assertEqual(args, ["-PjechJar=/checkouts/jech.jar", "-PjeiJar=/cache/jei.jar"])

    def test_jar_arguments_name_only_the_jech_jar_without_a_jei_mod(self):
        from lib.resolve import ResolvedArtifact, ResolvedEnv
        from pathlib import Path

        artifact = ResolvedArtifact(role="mod", name="fabric-api", source="curseforge",
                                    filename="fabric-api.jar", source_path=None,
                                    path=Path("/cache/fabric-api.jar"),
                                    sha1="x", size=1, detail="", via="dev-classpath")
        args = uitest_pipeline.jar_arguments(
            Path("/checkouts/jech.jar"), ResolvedEnv(env=None, artifacts=[artifact]))
        self.assertEqual(args, ["-PjechJar=/checkouts/jech.jar"])

    def test_jar_arguments_reject_a_jei_mod_without_a_jar(self):
        from lib.resolve import ResolvedArtifact, ResolvedEnv
        from pathlib import Path

        artifact = ResolvedArtifact(role="mod", name="jei", source="curseforge",
                                    filename="jei.jar", source_path=None, path=None,
                                    sha1="x", size=1, detail="", via="dev-classpath")
        with self.assertRaises(ConfigError) as caught:
            uitest_pipeline.jar_arguments(
                Path("/checkouts/jech.jar"), ResolvedEnv(env=None, artifacts=[artifact]))
        self.assertIn("without a jar path", str(caught.exception))

    def mod_artifact(self, name, path):
        from lib.resolve import ResolvedArtifact
        from pathlib import Path

        return ResolvedArtifact(role="mod", name=name, source="curseforge",
                                filename=f"{name}.jar", source_path=None, path=Path(path),
                                sha1="x", size=1, detail="", via="dev-classpath")

    def env_with_mods(self, *mods, loader=None):
        """The standalone env plus one extra mod entry per dict given."""
        data = standalone_dict()
        if loader:
            data["loader"] = {"type": loader, "version": "1"}
        for mod in mods:
            entry = {"source": "curseforge", "slug": mod["name"],
                     "describe": {"version": f"{mod['name']}.jar"}, "via": "dev-classpath"}
            entry.update(mod)
            data["mods"].append(entry)
        return envs.parse_env(data, "<unit>")

    def test_jar_arguments_carry_harness_tagged_mods_as_extra_jars(self):
        import os
        from lib.resolve import ResolvedEnv
        from pathlib import Path

        env = self.env_with_mods({"name": "jade", "tags": ["harness"]},
                                 {"name": "emi", "tags": ["harness"]})
        resolved = ResolvedEnv(env=env, artifacts=[
            self.mod_artifact("jei", "/cache/jei.jar"),
            self.mod_artifact("jade", "/cache/jade.jar"),
            self.mod_artifact("emi", "/cache/emi.jar")])
        args = uitest_pipeline.jar_arguments(Path("/checkouts/jech.jar"), resolved)
        self.assertEqual(args[:2], ["-PjechJar=/checkouts/jech.jar", "-PjeiJar=/cache/jei.jar"])
        self.assertEqual(args[2],
                         "-PjechExtraJars=" + os.pathsep.join(["/cache/jade.jar", "/cache/emi.jar"]))

    def test_jar_arguments_leave_the_extra_jars_out_without_the_tag(self):
        from lib.resolve import ResolvedEnv
        from pathlib import Path

        env = self.env_with_mods({"name": "jade"})
        resolved = ResolvedEnv(env=env, artifacts=[
            self.mod_artifact("jei", "/cache/jei.jar"),
            self.mod_artifact("jade", "/cache/jade.jar")])
        args = uitest_pipeline.jar_arguments(Path("/checkouts/jech.jar"), resolved)
        self.assertEqual(args, ["-PjechJar=/checkouts/jech.jar", "-PjeiJar=/cache/jei.jar",
                                "-Pneo_version=21.1.215"])

    def test_a_tagged_jei_stays_on_the_jei_argument_alone(self):
        from lib.resolve import ResolvedEnv
        from pathlib import Path

        data = standalone_dict()
        data["mods"][0]["tags"] = ["harness"]
        resolved = ResolvedEnv(env=envs.parse_env(data, "<unit>"),
                               artifacts=[self.mod_artifact("jei", "/cache/jei.jar")])
        args = uitest_pipeline.jar_arguments(Path("/checkouts/jech.jar"), resolved)
        self.assertEqual(args, ["-PjechJar=/checkouts/jech.jar", "-PjeiJar=/cache/jei.jar",
                                "-Pneo_version=21.1.215"])

    def test_the_env_loader_version_reaches_the_row_as_a_gradle_property(self):
        from lib.resolve import ResolvedEnv

        neoforge = ResolvedEnv(env=self.env("neoforge"), artifacts=[])
        fabric = ResolvedEnv(env=self.env("fabric"), artifacts=[])
        self.assertEqual(uitest_pipeline.loader_arguments(neoforge), ["-Pneo_version=1"])
        self.assertEqual(uitest_pipeline.loader_arguments(fabric), ["-Pfabric_loader_version=1"])

    def test_a_row_whose_loader_has_no_property_gets_no_argument(self):
        from lib.resolve import ResolvedEnv

        resolved = ResolvedEnv(env=self.env("forge"), artifacts=[])
        self.assertEqual(uitest_pipeline.loader_arguments(resolved), [])

    def library_artifact(self, filename):
        """A resolved platform-library jar whose filename is the real one (it carries the version)."""
        from lib.resolve import ResolvedArtifact
        from pathlib import Path

        return ResolvedArtifact(role="mod", name="fabric-api", source="curseforge",
                                filename=filename, source_path=None, path=Path("/cache") / filename,
                                sha1="x", size=1, detail="", via="dev-classpath")

    def test_a_pinned_platform_library_becomes_the_rows_own_property(self):
        """The fabric row pulls fabric-api itself, so the env's pin must travel as its property."""
        import os
        from lib.resolve import ResolvedEnv
        from pathlib import Path

        api = {"name": "fabric-api", "slug": "fabric-api", "tags": ["harness"],
               "describe": {"version": "0.116.7+1.21.1"},
               "pin": {"fileId": 7, "filename": "fabric-api-0.116.7+1.21.1.jar"}}
        env = self.env_with_mods(api, loader="fabric")
        resolved = ResolvedEnv(env=env, artifacts=[
            self.mod_artifact("jei", "/cache/jei.jar"),
            self.library_artifact("fabric-api-0.116.7+1.21.1.jar")])
        self.assertEqual(uitest_pipeline.library_arguments(resolved),
                         ["-Pfabric_version=0.116.7+1.21.1"])
        self.assertEqual(uitest_pipeline.extra_jar_paths(resolved), [])
        args = uitest_pipeline.jar_arguments(Path("/checkouts/jech.jar"), resolved)
        self.assertIn("-Pfabric_version=0.116.7+1.21.1", args)
        self.assertNotIn("-PjechExtraJars=" + os.pathsep.join(["/cache/fabric-api-0.116.7+1.21.1.jar"]),
                         args)

    def test_another_loaders_row_keeps_the_library_as_an_extra_jar(self):
        """The neoforge row reads no fabric_version, so there the same mod stays a jar."""
        from lib.resolve import ResolvedEnv
        from pathlib import Path

        api = {"name": "fabric-api", "slug": "fabric-api", "tags": ["harness"],
               "describe": {"version": "0.116.7+1.21.1"},
               "pin": {"fileId": 7, "filename": "fabric-api-0.116.7+1.21.1.jar"}}
        env = self.env_with_mods(api)
        resolved = ResolvedEnv(env=env, artifacts=[
            self.library_artifact("fabric-api-0.116.7+1.21.1.jar")])
        self.assertEqual(uitest_pipeline.library_arguments(resolved), [])
        self.assertEqual(uitest_pipeline.extra_jar_paths(resolved),
                         [Path("/cache/fabric-api-0.116.7+1.21.1.jar")])

    def test_the_description_carries_the_version_when_the_filename_does_not(self):
        from lib.resolve import ResolvedEnv

        api = {"name": "fabric-api", "slug": "fabric-api",
               "describe": {"version": "0.116.7+1.21.1"},
               "pin": {"fileId": 7, "filename": "fabricapi.jar"}}
        resolved = ResolvedEnv(env=self.env_with_mods(api, loader="fabric"), artifacts=[
            self.library_artifact("fabricapi.jar")])
        self.assertEqual(uitest_pipeline.library_arguments(resolved),
                         ["-Pfabric_version=0.116.7+1.21.1"])


if __name__ == "__main__":
    unittest.main()
