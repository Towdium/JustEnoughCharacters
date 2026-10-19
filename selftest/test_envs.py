"""The env schema: valid files load, every broken shape is rejected with detail."""

from __future__ import annotations

import json
import re
import tempfile
import unittest
from pathlib import Path

from lib import envs, paths
from lib.errors import EnvSchemaError

from . import support

# The single-mod instances (`tools/gen_envs.py`): one target mod each, named after
# its loader and the plan row's slug, all on the same row and the same selection as
# their line's baseline env. They are generated from the plan's roster, so the gate
# below derives their expectation from the name instead of listing 124 of them.
MOD_ENV_RE = re.compile(r"^1\.21\.1-(neoforge|fabric)-[a-z0-9._-]+$")
MOD_ENV_SELECTION = "tag:smoke,tag:jei"


def mod_env_row(name: str) -> str | None:
    """The standalone row a single-mod env runs on, from its own name; None if not one."""
    match = MOD_ENV_RE.match(name)
    return f"uitest/1.21.1-{match.group(1)}" if match else None


class EnvParsingTest(unittest.TestCase):
    def test_every_shipped_env_is_schema_valid(self):
        loaded = envs.load_all()
        self.assertGreaterEqual(len(loaded), 22, "the documented env matrix must ship")
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
        env = support.mk_env(timeout_sec=None)
        self.assertEqual(env.timeout_sec, envs.DEFAULT_TIMEOUT_SEC)
        self.assertEqual(env.gradle.task, ":neoforge:runClient")
        self.assertEqual(env.gradle.daemon_java, 21)

    def test_every_runnable_env_declares_the_one_launch_layer(self):
        """An env that names no launch layer at all is a configuration error.

        A row launches through its own standalone harness build or through a
        checkout Gradle run task, or it declares ``skip``; a row with none of the
        three cannot run, and saying so is better than launching nothing.
        """
        with self.assertRaises(EnvSchemaError) as caught:
            support.mk_env(gradle=None)
        message = str(caught.exception)
        self.assertIn("env must declare a launch layer", message)
        self.assertIn("skip", message)

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

    def test_uitest_is_optional_and_holds_one_selection(self):
        self.assertIsNone(support.mk_env().uitest)
        env = support.mk_env(gradle={"task": ":neoforge:runClientTests", "project_dir": "1.21.1"},
                             uitest={"selection": "tag:smoke,jei"})
        self.assertEqual(env.uitest.selection, "tag:smoke,jei")
        self.assertIn("tag:smoke,jei", repr(env.uitest))

    def test_uitest_files_hold_game_directory_files_to_stage(self):
        """A mod's own config, when its default opens a screen no headless client can click.

        Pixelmon's ``starter-on-join`` default opens its starter screen on every
        world join; ``Screen.isPauseScreen()`` is true for it, so the integrated
        server only runs ``tickPaused()`` and every scenario's first step times out.
        """
        env = support.mk_env(gradle={"task": ":neoforge:runClientTests", "project_dir": "1.21.1"},
                             uitest={"selection": "tag:smoke",
                                     "files": {"config/pixelmon/config.yml":
                                               "starter-on-join: false\n"}})
        self.assertEqual(env.uitest.files,
                         {"config/pixelmon/config.yml": "starter-on-join: false\n"})
        self.assertIn("config/pixelmon/config.yml", repr(env.uitest))

    def test_a_uitest_report_check_takes_an_optional_relative_path(self):
        default = support.mk_env(checks=[{"type": "uitest_report"}])
        self.assertEqual(default.checks[0].type, "uitest_report")
        self.assertIsNone(default.checks[0].value, "the harness's own location is the default")
        self.assertEqual(default.checks[0].name, "uitest_report")
        named = support.mk_env(checks=[{"type": "uitest_report", "value": "runs/report.json"}])
        self.assertEqual(named.checks[0].value, "runs/report.json")

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
            self.assertIsNone(loaded[name].world, name)
        self.assertIsNone(loaded["mv-1.16.5-forge"].world, "mv-1.16.5-forge")
        for name in ("1.21.1-neoforge", "1.21.1-fabric", "mv-1.20.1-fabric",
                     "mv-1.16.5-forge", "mv-1.20.1-forge", "mv-1.19.2-forge",
                     "mv-1.18.2-forge"):
            self.assertIsNone(loaded[name].skip, name)
        self.assertEqual(
            sorted(env.name for env in loaded.values() if env.skip),
            [],
            "every shipped row now has a launch layer of its own")
        self.assertIsNone(loaded["mv-1.19.2-fabric"].skip)
        self.assertIsNone(loaded["mv-1.19.2-fabric"].world)
        for name, marker in (("mv-1.16.5-forge", "no-transformer-failure"),
                             ("mv-1.18.2-forge", "no-transformer-failure"),
                             ("mv-1.19.2-forge", "jei-storage-registered"),
                             ("mv-1.20.1-forge", "jei-storage-registered")):
            row = loaded[name]
            self.assertIsNone(row.gradle, f"{name} carries its own launch layer")
            self.assertIn(marker, [check.name for check in row.checks], name)
        for name, marker in (("1.12.2-forge-jei-legacy", "legacy-tree-transformed"),
                             ("1.12.2-forge-hei", "jei-index-registered")):
            row = loaded[name]
            self.assertEqual(row.uitest.project, "uitest/1.12.2-forge",
                             f"{name} runs the standalone harness build")
            self.assertIsNone(row.gradle, f"{name} carries its own launch layer")
            self.assertIn(marker, [check.name for check in row.checks], name)

    def test_every_shipped_env_declares_a_stopping_rule(self):
        """No env that is actually executed may be left without ``ready_when``.

        A modded client that reaches the title screen never exits on its own, so
        an env without a stopping rule can only end in a watchdog kill (exit
        code 3) — the run is bounded by ``timeout_sec`` even when the failure it
        should report is already in the log. That makes a missing ``ready_when``
        a broken env, not a lenient one. A skipped env is the exception: it is
        never executed, so it needs no stopping rule, and its checks stay
        written down as documentation.

        An env that runs the in-client harness is the second exception, and it is
        the mirror image of the rule: the harness *is* the in-game runner, it
        quits the client itself once the selection is done, and a stopping rule
        would end the session mid-scenario and leave the run without a report at
        all. Such an env must therefore declare none.
        """
        for env in envs.load_all():
            if env.skip:
                continue
            if env.uitest is not None:
                self.assertIsNone(
                    env.ready_when,
                    f"{env.name} runs the in-client harness, which quits the client itself: a "
                    "ready_when would stop the session mid-scenario and leave no report")
                continue
            self.assertIsNotNone(env.ready_when, f"{env.name} declares no ready_when")

    def test_the_shipped_harness_rows_declare_what_they_run(self):
        """``uitest`` is a row's own statement of which scenarios it was measured with.

        The selection names scenarios rather than saying ``all``, for the same
        reason a row's ``world`` is written down: a row asserts what it was
        measured with, and a scenario added later cannot silently change that.
        The row also has to carry the check that reads the harness's report —
        judged by the log alone it would pass on a client that never ran one.

        The selection is per row, not a constant: the two 1.12.2 rows answer JEI
        searches through different machinery (legacy JEI through its suffix-tree
        replacement, HEI through ``IAdvancedSearchRegistry``), so only the
        legacy-JEI row claims the scenarios that assert on a recorded pinyin
        search. The HEI row runs the smoke scenarios, which is what it was
        measured with.

        The two 1.21.1 rows are the standalone backend's first rows (since
        2026-10-15), and the two ``moderner-1.21.10`` rows joined them the same
        day when their checkout copies moved out: their ``uitest`` block names
        the harness build it runs, so the row carries its own launch layer and
        declares no ``gradle`` block at all.
        The 26.1.2 pair joined them on 2026-10-15, ported to the
        ``uitest/moderner-26.1.2-{neoforge,fabric}`` builds, and the 1.12.2 pair
        joined the same day: the two rows share one build
        (``uitest/1.12.2-forge``) — the row is picked by the JEI-line jar jt
        hands over as ``-PjeiJar``. The two mv fabric rows 1.20.1 and 1.19.2
        joined the same day (``uitest/mv-1.20.1-fabric``,
        ``uitest/mv-1.19.2-fabric``), and the three multiple-version forge rows
        joined with them (``uitest/mv-{1.18.2,1.19.2,1.20.1}-forge``), all three
        answering the JEI tag: the 1.18.2 row's JECh build carries no JEI plugin
        class (measured 2026-10-14), but its JEI bridge is the legacy ASM coremod
        plus ``FakeTree``, which the harness taps itself, and both its JEI
        scenarios are green (measured 2026-10-16). The last two mv fabric rows
        1.18.2 and 1.16.5 followed to ``uitest/mv-1.18.2-fabric`` and
        ``uitest/mv-1.16.5-fabric``: the 1.18.2 row runs the three scenarios its
        build carries, while the 1.16.5 row is the matrix's one row with no
        target mod at all — its build carries a single scenario and no JEI
        reaches the run — so it claims the smoke tag alone, and claiming
        ``tag:jei`` there would name scenarios the build does not have. Every
        selection names scenarios, never every scenario the build carries.
        ``mv-1.16.5-forge`` joined them on 2026-10-16 (its own FG6 build,
        ``uitest/mv-1.16.5-forge``). It answers the smoke tag alone too: this
        line's JECh jar carries no JEI plugin class (measured 2026-10-15 — no
        JechSearchStorage; its JEI bridge is the legacy ASM coremod), and the
        JEI scenarios focus JEI's search field with a measured mouse click,
        which this row has not been measured with yet.
        The 17 ``*-mods-*`` rows joined on 2026-10-19, each on its version line's
        own build (``uitest/1.21.1-neoforge`` / ``uitest/1.21.1-fabric``): what
        they add is the env's other mods, which that build loads for the same
        reason it loads the jars under test — jt hands them over as
        ``-PjechExtraJars``, driven by a mod entry's ``tags: ["harness"]``.
        """
        expected = {
            "1.12.2-forge-hei": "tag:smoke",
            "1.12.2-forge-jei-legacy": "tag:smoke,tag:jei",
            "1.21.1-neoforge": "tag:smoke,tag:jei",
            "1.21.1-fabric": "tag:smoke,tag:jei",
            "moderner-1.21.10-fabric": "tag:smoke,tag:jei",
            "moderner-1.21.10-neoforge": "tag:smoke,tag:jei",
            "moderner-1.21.11-fabric": "tag:smoke,tag:jei",
            "moderner-1.21.11-neoforge": "tag:smoke,tag:jei",
            "moderner-26.1.2-neoforge": "tag:smoke,tag:jei",
            "moderner-26.1.2-fabric": "tag:smoke,tag:jei",
            "moderner-26.2-neoforge": "tag:smoke,tag:jei",
            "moderner-26.2-fabric": "tag:smoke,tag:jei",
            "moderner-26.3-neoforge": "tag:smoke,tag:jei",
            "moderner-26.3-fabric": "tag:smoke,tag:jei",
            "mv-1.16.5-fabric": "tag:smoke",
            "mv-1.16.5-forge": "tag:smoke",
            "mv-1.18.2-fabric": "tag:smoke,tag:jei",
            "mv-1.18.2-forge": "tag:smoke,tag:jei",
            "mv-1.19.2-forge": "tag:smoke,tag:jei",
            "mv-1.20.1-forge": "tag:smoke,tag:jei",
            "mv-1.19.2-fabric": "tag:smoke,tag:jei",
            "mv-1.20.1-fabric": "tag:smoke,tag:jei",
        }
        standalone = {
            "1.12.2-forge-hei": "uitest/1.12.2-forge",
            "1.12.2-forge-jei-legacy": "uitest/1.12.2-forge",
            "1.21.1-neoforge": "uitest/1.21.1-neoforge",
            "1.21.1-fabric": "uitest/1.21.1-fabric",
            "moderner-1.21.10-neoforge": "uitest/moderner-1.21.10-neoforge",
            "moderner-1.21.10-fabric": "uitest/moderner-1.21.10-fabric",
            "moderner-1.21.11-neoforge": "uitest/moderner-1.21.11-neoforge",
            "moderner-1.21.11-fabric": "uitest/moderner-1.21.11-fabric",
            "moderner-26.1.2-neoforge": "uitest/moderner-26.1.2-neoforge",
            "moderner-26.1.2-fabric": "uitest/moderner-26.1.2-fabric",
            "moderner-26.2-neoforge": "uitest/moderner-26.2-neoforge",
            "moderner-26.2-fabric": "uitest/moderner-26.2-fabric",
            "moderner-26.3-neoforge": "uitest/moderner-26.3-neoforge",
            "moderner-26.3-fabric": "uitest/moderner-26.3-fabric",
            "mv-1.16.5-fabric": "uitest/mv-1.16.5-fabric",
            "mv-1.16.5-forge": "uitest/mv-1.16.5-forge",
            "mv-1.18.2-fabric": "uitest/mv-1.18.2-fabric",
            "mv-1.18.2-forge": "uitest/mv-1.18.2-forge",
            "mv-1.19.2-forge": "uitest/mv-1.19.2-forge",
            "mv-1.20.1-forge": "uitest/mv-1.20.1-forge",
            "mv-1.19.2-fabric": "uitest/mv-1.19.2-fabric",
            "mv-1.20.1-fabric": "uitest/mv-1.20.1-fabric",
        }
        standalone.update({env.name: mod_env_row(env.name) for env in envs.load_all()
                           if mod_env_row(env.name)})
        expected.update({env.name: MOD_ENV_SELECTION for env in envs.load_all() if mod_env_row(env.name)})
        rows = {env.name: env for env in envs.load_all() if env.uitest is not None}
        self.assertEqual(sorted(rows), sorted(expected),
                         "a row that runs the in-client harness is a decision, not a default")
        for name, env in rows.items():
            self.assertEqual(env.uitest.selection, expected[name], name)
            self.assertNotEqual(env.uitest.selection, "all", name)
            for term in env.uitest.selection.split(","):
                self.assertTrue(":" in term,
                                f"{name}: {term!r} has no selector prefix, so it can only match a "
                                "scenario name")
            self.assertIn("uitest_report", [check.type for check in env.checks], name)
            if name in standalone:
                self.assertEqual(env.uitest.project, standalone[name], name)
                self.assertIsNone(env.gradle, f"{name} carries its own launch layer")
            else:
                self.assertIsNone(env.uitest.project, name)

    def test_the_shipped_matrix_declares_a_world_only_where_it_was_measured(self):
        """``world`` is a premise about the marker, not a decoration.

        The rows launched into a level are exactly the ones whose JEI version
        defers its registration phase to level load and that can take a quick
        play argument (1.20+); the rest assert startup markers and must stay
        without one, so a row cannot silently acquire a level load it was not
        measured with. ``1.21.1-neoforge`` left the set when it moved to the
        in-client harness, and both ``moderner-1.21.10`` rows followed it there;
        ``1.21.1-fabric`` joined them on 2026-10-15 when it moved to the
        standalone harness build, and the three multiple-version forge rows
        (``mv-1.18.2/1.19.2/1.20.1-forge``) plus ``mv-1.20.1-fabric`` left it
        the same way: their level is the harness's own, and a quick-play join
        would race the level the scenarios are asserted against. Every shipped
        row now owns its level, so none declares a world and the measured set
        is empty.
        """
        measured = set()
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

    def test_uitest_files_path_must_stay_inside_the_game_directory(self):
        self.reject(support.env_dict(uitest={"selection": "all",
                                             "files": {"../pixelmon/config.yml": "x"}}),
                    "env.uitest.files path", "not accepted")

    def test_uitest_files_content_must_not_be_empty(self):
        self.reject(support.env_dict(uitest={"selection": "all",
                                             "files": {"config/pixelmon/config.yml": ""}}),
                    "env.uitest.files['config/pixelmon/config.yml'] must be a non-empty string")

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

    def test_uitest_needs_an_object_and_a_selection(self):
        self.reject(support.env_dict(uitest="all"), "env.uitest must be an object")
        self.reject(support.env_dict(uitest={"sel": "all"}), "unknown key 'sel'")
        self.reject(support.env_dict(uitest={}), "missing required key 'selection'")
        self.reject(support.env_dict(uitest={"selection": "  "}), "must be a non-empty string")
        self.reject(support.env_dict(uitest={"selection": "a\nb"}),
                    "one line without control characters")

    def test_uitest_without_a_gradle_block_is_rejected(self):
        """The selection is a Gradle property: with no launch layer there is nowhere to put it."""
        self.reject(support.env_dict(gradle=None, skip="waiting on a launch path",
                                     uitest={"selection": "all"}),
                    "env.uitest needs a launch layer")

    def test_uitest_report_path_must_stay_relative(self):
        self.reject(support.env_dict(checks=[{"type": "uitest_report", "value": "/tmp/report.json"}]),
                    "absolute path")
        self.reject(support.env_dict(checks=[{"type": "uitest_report", "value": "   "}]),
                    "must be a non-empty path")

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
    """``env.gradle`` names the launch layer, and every runnable env needs one."""

    def reject(self, data, *needles):
        with self.assertRaises(EnvSchemaError) as caught:
            envs.parse_env(data, "<unit>")
        message = str(caught.exception)
        for needle in needles:
            self.assertIn(needle, message, f"expected {needle!r} in:\n{message}")
        return message

    def test_a_skipped_env_needs_no_run_task(self):
        """A row that is never executed needs no launch layer — only a reason."""
        env = support.mk_env(gradle=None, skip="no launch path yet; see envs/README.md")
        self.assertIsNone(env.gradle)

    def test_a_runnable_env_without_the_block_is_a_config_error(self):
        message = self.reject(support.env_dict(gradle=None), "env must declare a launch layer")
        self.assertIn("skip", message, "the error must name the other honest state")

    def test_the_block_names_the_task_the_checkout_and_the_gradle_jdk(self):
        env = support.mk_env(gradle={"task": ":neoforge:runClient", "project_dir": "1.21.1",
                                     "daemon_java": 21})
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

    def test_the_block_may_carry_extra_gradle_arguments(self):
        """``args`` is the passthrough for a project property the checkout reads.

        jt's gradle backend assembles ``-PjechRunDir``/``-PjechQuickPlayWorld``/
        ``-PjechTest`` itself, because every checkout spells those the same way;
        whatever else a row needs travels here, as written, after them.
        """
        env = support.mk_env(gradle={"task": "runClientTests", "project_dir": "1.12.0",
                                     "daemon_java": None, "args": ["-PdevMod=jei"]})
        self.assertEqual(env.gradle.args, ("-PdevMod=jei",))
        self.assertEqual(support.mk_env().gradle.args, (),
                         "no args means an empty list, never None")

    def test_extra_gradle_arguments_must_be_a_list_of_non_empty_strings(self):
        self.reject(support.env_dict(gradle={"task": "runClient", "args": "-PdevMod=jei"}),
                    "env.gradle.args must be a list")
        self.reject(support.env_dict(gradle={"task": "runClient", "args": {"a": 1}}),
                    "env.gradle.args must be a list")
        self.reject(support.env_dict(gradle={"task": "runClient", "args": ["  "]},
                                     ), "env.gradle.args must be a list of non-empty strings")
        self.reject(support.env_dict(gradle={"task": "runClient", "args": [3]}),
                    "env.gradle.args must be a list of non-empty strings")

    def test_the_112_rows_name_the_jei_line_mod_they_were_measured_with(self):
        """The two 1.12.2 rows pick their JEI-line mod by the jar they pin.

        Since 2026-10-15 both rows run the standalone harness build
        (``uitest/1.12.2-forge``), so which JEI-line mod a run loads is no longer
        a checkout property — ``-PdevMod`` is gone. The choice is the pinned jar
        jt hands over as ``-PjeiJar``, and the build stages whatever jar it is
        given (its filename is the row). The mod entry's *name* is ``jei`` on
        both rows — the standalone backend finds its ``-PjeiJar`` target by that
        name (``uitest_pipeline.JEI_MOD_NAME``) — while the pin beneath it names
        the actual artifact: legacy JEI on one row, Had Enough Items on the
        other.
        """
        loaded = {env.name: env for env in envs.load_all()}
        pinned = {
            "1.12.2-forge-jei-legacy": "jei_1.12.2-4.16.1.301.jar",
            "1.12.2-forge-hei": "HadEnoughItems_1.12.2-4.34.0.jar",
        }
        for name, filename in pinned.items():
            row = loaded[name]
            self.assertIsNone(row.gradle, f"{name} passes no checkout properties anymore")
            self.assertEqual(row.uitest.project, "uitest/1.12.2-forge", name)
            self.assertEqual([m.name for m in row.mods], ["jei"], name)
            self.assertEqual(row.mods[0].pin["filename"], filename, name)
        for env in loaded.values():
            if env.gradle is not None:
                self.assertEqual(env.gradle.args, (),
                                 f"{env.name}: a row that needs no property passes none")

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
        """Four rows are the standalone dev-classpath contract, written down.

        Since 2026-10-15 the two 1.21.1 rows and the two 1.12.2 rows run
        standalone harness builds (``uitest/1.21.1-{neoforge,fabric}``,
        ``uitest/1.12.2-forge``), which load every jar themselves: the 1.21.1
        neoforge build rides the two jars on the run's legacy classpath
        (``localRuntime``), the 1.21.1 fabric build stages them into the run's
        ``mods/`` itself and takes fabric-api as its own Gradle dependency at
        the pinned version. The three multiple-version forge rows that moved to
        the standalone backend are the same contract: their MDG-legacyforge
        builds remap and load both jars themselves (``modRuntimeOnly`` off a
        content-hashed flatDir repository inside the build).

        The 1.12.2 build rides the JECh srg jar on the run task's classpath
        (FML's ``ModDiscoverer.findClasspathMods`` discovers the mod in it) while
        the JEI-line jar is staged into the game directory's ``mods/`` by the
        harness run itself, with its access transformer remapped for the dev run
        (its published rules are SRG-named and meet MCP-named members, which is
        an ``IllegalAccessError`` in JEI's own init — measured, see the rows' own
        comments); the row passes ``-PdevMod`` so the checkout stages the one it
        was measured with. Nothing is staged by jt on any of these rows — a
        staged copy would double-load — so every artifact is ``dev-classpath``.
        """
        loaded = {env.name: env for env in envs.load_all()}
        for name in ("1.21.1-neoforge", "1.21.1-fabric",
                     "mv-1.18.2-forge", "mv-1.19.2-forge", "mv-1.20.1-forge"):
            row = loaded[name]
            self.assertEqual(row.jech.via, "dev-classpath", name)
            self.assertTrue(all(mod.via == "dev-classpath" for mod in row.mods), name)

        for name in ("1.12.2-forge-jei-legacy", "1.12.2-forge-hei"):
            row = loaded[name]
            self.assertIsNone(row.gradle, f"{name} carries its own launch layer")
            self.assertEqual(row.uitest.project, "uitest/1.12.2-forge", name)
            self.assertEqual(row.jech.via, "dev-classpath", name)
            self.assertEqual({m.name: m.via for m in row.mods}, {"jei": "dev-classpath"}, name)

    def test_every_shipped_row_is_standalone_or_skipped(self):
        """The matrix has exactly two states left: a standalone build, or a skip.

        The Prism instance launcher was removed on 2026-10-14, so a row that names
        no launch layer at all would be an env that cannot run and does not say why.
        Every row has moved to the standalone backend — the
        1.21.1 pair led on 2026-10-15, then both moderner pairs (1.21.10, 26.1.2),
        the 1.12.2 pair (sharing ``uitest/1.12.2-forge``), the mv fabric rows
        1.20.1 and 1.19.2, the mv forge rows 1.18.2/1.19.2/1.20.1, the mv fabric
        rows 1.18.2 and 1.16.5, and finally ``mv-1.16.5-forge`` on 2026-10-16 —
        each ``uitest`` block naming ``uitest/<line>``, an independent Gradle
        build in this repository that carries its own run task. No row declares a
        ``gradle`` run task and no row is skipped any more, which is why
        ``gradle_rows`` is empty: the closure is named rather than counted, so a
        row that silently reclaims or loses a launch layer cannot pass this test.
        """
        loaded = {env.name: env for env in envs.load_all()}
        gradle_rows = set()
        standalone_rows = {
            "1.12.2-forge-hei": "uitest/1.12.2-forge",
            "1.12.2-forge-jei-legacy": "uitest/1.12.2-forge",
            "1.21.1-neoforge": "uitest/1.21.1-neoforge",
            "1.21.1-fabric": "uitest/1.21.1-fabric",
            "moderner-1.21.10-neoforge": "uitest/moderner-1.21.10-neoforge",
            "moderner-1.21.10-fabric": "uitest/moderner-1.21.10-fabric",
            "moderner-1.21.11-neoforge": "uitest/moderner-1.21.11-neoforge",
            "moderner-1.21.11-fabric": "uitest/moderner-1.21.11-fabric",
            "moderner-26.1.2-neoforge": "uitest/moderner-26.1.2-neoforge",
            "moderner-26.1.2-fabric": "uitest/moderner-26.1.2-fabric",
            "moderner-26.2-neoforge": "uitest/moderner-26.2-neoforge",
            "moderner-26.2-fabric": "uitest/moderner-26.2-fabric",
            "moderner-26.3-neoforge": "uitest/moderner-26.3-neoforge",
            "moderner-26.3-fabric": "uitest/moderner-26.3-fabric",
            "mv-1.16.5-fabric": "uitest/mv-1.16.5-fabric",
            "mv-1.16.5-forge": "uitest/mv-1.16.5-forge",
            "mv-1.18.2-fabric": "uitest/mv-1.18.2-fabric",
            "mv-1.18.2-forge": "uitest/mv-1.18.2-forge",
            "mv-1.19.2-fabric": "uitest/mv-1.19.2-fabric",
            "mv-1.19.2-forge": "uitest/mv-1.19.2-forge",
            "mv-1.20.1-fabric": "uitest/mv-1.20.1-fabric",
            "mv-1.20.1-forge": "uitest/mv-1.20.1-forge",
        }
        standalone_rows.update({env.name: mod_env_row(env.name) for env in envs.load_all()
                                if mod_env_row(env.name)})
        self.assertFalse(gradle_rows - set(loaded), "a named gradle row does not exist")
        for env in loaded.values():
            if env.name in gradle_rows:
                self.assertIsNotNone(env.gradle, env.name)
                self.assertIsNone(env.skip, env.name)
            elif env.name in standalone_rows:
                self.assertIsNone(env.gradle, f"{env.name} carries its own launch layer")
                self.assertEqual(env.uitest.project, standalone_rows[env.name], env.name)
                self.assertIsNone(env.skip, env.name)
            else:
                self.assertIsNotNone(env.skip,
                                     f"{env.name} has neither a run task, a standalone build, "
                                     "nor a skip reason")
        self.assertEqual([name for name, env in loaded.items() if env.gradle is not None],
                         sorted(gradle_rows))

    def test_the_mv_fabric_rows_ride_the_classpath_on_their_own_builds(self):
        """All four multiple-version fabric rows, on the standalone backend.

        Each moved off the checkout's ``:<mc>:fabric:runClient`` for its own
        ``uitest/mv-<mc>-fabric`` build (the 1.20.1 and 1.19.2 rows on
        2026-10-15, then 1.18.2 and 1.16.5 the next day), so none carries a
        ``gradle`` block and none stages anything: the harness build loads every
        jar itself, which is why JECh and every declared mod ride
        ``dev-classpath``. JEI is declared only where the row has one — the 1.16.5
        smoke row has no target mod at all — and fabric-api rides the build's own
        dependency. See each row's env comment.
        """
        loaded = {env.name: env for env in envs.load_all()}
        expected = {
            "mv-1.16.5-fabric": {"fabric-api": "dev-classpath"},
            "mv-1.18.2-fabric": {"jei": "dev-classpath", "fabric-api": "dev-classpath"},
            "mv-1.19.2-fabric": {"jei": "dev-classpath", "fabric-api": "dev-classpath"},
            "mv-1.20.1-fabric": {"jei": "dev-classpath", "fabric-api": "dev-classpath"},
        }
        for name, vias in expected.items():
            env = loaded[name]
            self.assertIsNone(env.gradle, f"{name} carries its own launch layer")
            self.assertEqual(env.uitest.project, f"uitest/{name}", name)
            self.assertEqual(env.jech.via, "dev-classpath", name)
            self.assertEqual({mod.name: mod.via for mod in env.mods}, vias, name)

    def test_the_moderner_rows_ride_the_classpath_too(self):
        """The moderner 1.21.10 rows are the same contract, in the modern line.

        The 26.1.2 pair joined the standalone backend the same day
        (``uitest/moderner-26.1.2-{neoforge,fabric}`` builds), so no moderner
        row carries a ``gradle`` block anymore: the harness builds load every
        jar themselves, and the env's pins ride their classpaths at exactly the
        versions the rows declare.

        The two 1.21.10 rows moved to the standalone backend (2026-10-15, the
        same day the 1.21.1 pair did), so they no longer carry a ``gradle``
        block; the dev-classpath contract is unchanged on them — the harness
        build loads every jar itself (the neoforge build rides both jars on its
        run's legacy classpath; the fabric build stages them into ``mods/`` and
        takes fabric-api as its own dependency at the pinned version).
        """
        loaded = {env.name: env for env in envs.load_all()}
        standalone = {
            "moderner-1.21.10-neoforge": "uitest/moderner-1.21.10-neoforge",
            "moderner-1.21.10-fabric": "uitest/moderner-1.21.10-fabric",
            "moderner-1.21.11-neoforge": "uitest/moderner-1.21.11-neoforge",
            "moderner-1.21.11-fabric": "uitest/moderner-1.21.11-fabric",
            "moderner-26.1.2-neoforge": "uitest/moderner-26.1.2-neoforge",
            "moderner-26.1.2-fabric": "uitest/moderner-26.1.2-fabric",
            "moderner-26.2-neoforge": "uitest/moderner-26.2-neoforge",
            "moderner-26.2-fabric": "uitest/moderner-26.2-fabric",
            "moderner-26.3-neoforge": "uitest/moderner-26.3-neoforge",
            "moderner-26.3-fabric": "uitest/moderner-26.3-fabric",
        }
        for name, project in standalone.items():
            env = loaded[name]
            self.assertIsNone(env.gradle, f"{name} carries its own launch layer")
            self.assertEqual(env.uitest.project, project, name)
        for name in ("moderner-1.21.10-neoforge", "moderner-1.21.10-fabric",
                     "moderner-1.21.11-neoforge", "moderner-1.21.11-fabric",
                     "moderner-26.1.2-neoforge", "moderner-26.1.2-fabric",
                     "moderner-26.2-neoforge", "moderner-26.2-fabric",
                     "moderner-26.3-neoforge", "moderner-26.3-fabric"):
            env = loaded[name]
            self.assertEqual(env.jech.via, "dev-classpath", name)
            for mod in env.mods:
                self.assertEqual(mod.via, "dev-classpath", f"{name}/{mod.name}")


class CheckoutWiringTest(unittest.TestCase):
    """The gradle contract: the harness's properties must be read by the checkout.

    Read-only and layout-tolerant: with the workstation layout (one checkout per
    version line under the JECh root) the build files are found and checked; in a
    CI checkout, where the branch root already *is* the version line, the module
    path does not resolve this way and the check is skipped rather than faked.

    The paths are spelled out per module rather than derived from an env's
    ``gradle`` block: the 1.21.1 rows moved to the standalone backend
    (2026-10-15) and no longer carry one, but the checkout wiring itself is still
    this contract — it is what any manual run in those checkouts still uses.
    """

    def module_gradle(self, project_dir, module):
        from lib import resolve
        return resolve.jech_base_root() / project_dir / module / "build.gradle"

    def test_both_1_21_1_modules_read_the_harness_properties(self):
        from lib import instance
        checked = []
        for project_dir, module in (("1.21.1", "neoforge"), ("1.21.1", "fabric")):
            path = self.module_gradle(project_dir, module)
            if not path.is_file():
                continue
            text = path.read_text(encoding="utf-8")
            checked.append(module)
            for needle in (instance.GRADLE_RUN_DIR_PROPERTY,
                           instance.GRADLE_QUICK_PLAY_PROPERTY):
                self.assertIn(needle, text, f"{module}: {path} does not read {needle!r}")
            self.assertIn("isPresent()", text, f"{module}: the wiring is not property-guarded")
        self.assertTrue(checked, "no checkout found; the wiring contract was not verified")

    def test_the_multiple_version_fabric_modules_read_the_harness_properties(self):
        """The same contract inside the ``multiple-version`` checkout.

        Its fabric modules carry the wiring, so every one of them reads both
        properties and only adds them when they are present: a plain
        ``./gradlew :1.16.5:fabric:runClient`` keeps the module's own run
        directory and joins nothing. The module paths are spelled out rather than
        derived from an env's ``gradle`` block — all four rows moved to their own
        standalone harness builds (2026-10-15/16) and no longer carry one, but the
        checkout wiring is still this contract for a manual run until W3 removes
        the embedded harness, so the walk is layout-driven and skips a module whose
        build file is not in this checkout.

        The trailing ``checked`` assertion keeps it honest: it fails rather than
        passing vacuously if the table empties.
        """
        from lib import instance
        checked = []
        modules = {
            "mv-1.16.5-fabric": ("multiple-version", "1.16.5/fabric"),
            "mv-1.18.2-fabric": ("multiple-version", "1.18.2/fabric"),
            "mv-1.19.2-fabric": ("multiple-version", "1.19.2/fabric"),
            "mv-1.20.1-fabric": ("multiple-version", "1.20.1/fabric"),
        }
        for name, (project_dir, module) in modules.items():
            path = self.module_gradle(project_dir, module)
            if not path.is_file():
                continue
            text = path.read_text(encoding="utf-8")
            checked.append(name)
            for needle in (instance.GRADLE_RUN_DIR_PROPERTY,
                           instance.GRADLE_QUICK_PLAY_PROPERTY):
                self.assertIn(needle, text, f"{name}: {path} does not read {needle!r}")
            self.assertIn("isPresent()", text, f"{name}: the wiring is not property-guarded")
        self.assertTrue(checked, "no checkout found; the wiring contract was not verified")

    def test_the_moderner_modules_read_the_harness_properties(self):
        """The same contract for the moderner modules still on the gradle backend.

        Two details are specific to that line and are asserted here so a later edit
        cannot quietly drop them: the neoforge module *already* had a quick-play
        property of its own (``quickPlaySingleplayerWorld``) and a fixed
        ``run/client/Dev`` game directory, both of which must survive next to the
        harness's ``jechRunDir``/``jechQuickPlayWorld``, and the fabric pair had no
        run configuration at all before — its wiring only adds the two properties
        to loom's default client run.

        All four moderner rows moved to the standalone backend (2026-10-15) and
        no longer carry a ``gradle`` block, but all four modules' wiring is still
        this contract for a manual run until W3 removes the embedded harness —
        so this walk is layout-driven, never derived from an env's ``gradle``
        block.
        """
        from lib import instance
        checked = []
        modules = {
            "moderner-1.21.10-neoforge": ("moderner", "neoforge"),
            "moderner-1.21.10-fabric": ("moderner", "fabric-legacy"),
            "moderner-26.1.2-neoforge": ("moderner", "neoforge-unobfuscated"),
            "moderner-26.1.2-fabric": ("moderner", "fabric-unobfuscated"),
        }
        for name, (project_dir, module) in modules.items():
            path = self.module_gradle(project_dir, module)
            if not path.is_file():
                continue
            text = path.read_text(encoding="utf-8")
            checked.append(name)
            for needle in (instance.GRADLE_RUN_DIR_PROPERTY,
                           instance.GRADLE_QUICK_PLAY_PROPERTY):
                self.assertIn(needle, text, f"{name}: {path} does not read {needle!r}")
            self.assertIn("isPresent()", text, f"{name}: the wiring is not property-guarded")
            if "neoforge" in module:
                self.assertIn("quickPlaySingleplayerWorld", text,
                              f"{name}: the checkout's own quick-play property was dropped")
                self.assertIn("run/client/Dev", text,
                              f"{name}: the checkout's own default game directory was dropped")
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
        path = self.module_gradle("1.21.1", "fabric")
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
