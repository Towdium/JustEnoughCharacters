"""mrpack rendering: index shape, overrides-only layout, hashes."""

from __future__ import annotations

import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from lib import options, pack
from lib.errors import ConfigError, DependencyError

from . import support


class PackTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = Path(self._tmp.name)

    def build(self, *, env=None, jars=(("jei", "jei.jar"), ("jech", "jecharacters.jar")),
              seed=None):
        env = env or support.mk_env()
        artifacts = []
        for index, (name, filename) in enumerate(jars):
            jar = support.write_jar(self.tmp / "cache" / filename, mod_id=name)
            artifacts.append(support.artifact(name, jar, role="jech" if name == "jech" else "mod"))
        resolved = support.resolved_env(env, artifacts)
        run_dir = self.tmp / "runs" / "unit-run"
        result = pack.build_pack(env, resolved, run_dir, seed=seed, log=lambda *_: None)
        return env, resolved, result, run_dir

    def test_index_declares_the_loader_and_leaves_files_empty(self):
        env, _resolved, result, _run_dir = self.build()
        index = pack.read_index(result.mrpack_path)
        self.assertEqual(index["formatVersion"], 1)
        self.assertEqual(index["game"], "minecraft")
        self.assertEqual(index["name"], env.name)
        self.assertEqual(index["dependencies"], {"minecraft": "1.21.1", "neoforge": "21.1.215"})
        self.assertEqual(index["files"], [], "every file must live in overrides, never in files[]")
        self.assertEqual(index["versionId"], env.name)

    def test_every_jar_lands_in_overrides_mods(self):
        _env, _resolved, result, _run_dir = self.build()
        listed = pack.listed_mods(result.mrpack_path)
        self.assertEqual(listed, ["overrides/mods/jecharacters.jar", "overrides/mods/jei.jar"])
        with zipfile.ZipFile(result.mrpack_path) as archive:
            names = archive.namelist()
        self.assertIn("modrinth.index.json", names)
        self.assertTrue(all(not name.startswith("files/") for name in names))

    def test_the_staged_build_directory_is_kept_next_to_the_zip(self):
        _env, _resolved, result, run_dir = self.build()
        self.assertEqual(result.pack_dir, run_dir / "pack")
        self.assertTrue((result.pack_dir / "modrinth.index.json").is_file())
        self.assertTrue((result.pack_dir / "overrides" / "mods" / "jei.jar").is_file())
        self.assertEqual(result.mrpack_path, run_dir / "unit-env.mrpack")

    def test_staging_never_mutates_the_cache(self):
        _env, resolved, _result, _run_dir = self.build()
        for artifact in resolved.artifacts:
            before = artifact.path.stat().st_mtime_ns
            staged = self.tmp / "runs" / "unit-run" / "pack" / "overrides" / "mods" / artifact.filename
            self.assertEqual(staged.stat().st_mtime_ns, before)

    def test_loader_dependency_keys(self):
        self.assertEqual(pack.dependency_key("forge"), "forge")
        self.assertEqual(pack.dependency_key("neoforge"), "neoforge")
        self.assertEqual(pack.dependency_key("fabric"), "fabric-loader")
        self.assertEqual(pack.dependency_key("quilt"), "quilt-loader")
        with self.assertRaises(ConfigError):
            pack.dependency_key("liteloader")

    def test_forge_1_12_2_env_renders_the_forge_dependency(self):
        env = support.mk_env(mc="1.12.2", loader={"type": "forge", "version": "14.23.5.2860"},
                             java={"version": 8})
        _env, _resolved, result, _run_dir = self.build(env=env)
        self.assertEqual(pack.read_index(result.mrpack_path)["dependencies"],
                         {"minecraft": "1.12.2", "forge": "14.23.5.2860"})

    def test_two_artifacts_with_one_filename_are_rejected(self):
        env = support.mk_env()
        jar_a = support.write_jar(self.tmp / "a" / "same.jar", mod_id="one")
        jar_b = support.write_jar(self.tmp / "b" / "same.jar", mod_id="two")
        resolved = support.resolved_env(env, [support.artifact("one", jar_a),
                                              support.artifact("two", jar_b)])
        with self.assertRaises(DependencyError):
            pack.build_pack(env, resolved, self.tmp / "runs" / "dup", log=lambda *_: None)

    def test_a_missing_cache_file_is_an_error_not_an_empty_pack(self):
        env = support.mk_env()
        ghost = support.artifact("jei", self.tmp / "absent.jar")
        resolved = support.resolved_env(env, [ghost])
        with self.assertRaises(DependencyError):
            pack.build_pack(env, resolved, self.tmp / "runs" / "ghost", log=lambda *_: None)

    def test_modset_hash_is_stable_and_content_sensitive(self):
        _env, resolved, result, _run_dir = self.build()
        self.assertEqual(result.pack_hash, pack.modset_hash(resolved.env, resolved))
        other = support.mk_env()
        jar = support.write_jar(self.tmp / "other.jar", mod_id="jei")
        changed = support.resolved_env(other, [support.artifact("jei", jar, sha1="0" * 40)])
        self.assertNotEqual(pack.modset_hash(other, changed), result.pack_hash)

    def test_modset_hash_ignores_timestamps(self):
        env = support.mk_env()
        jar = support.write_jar(self.tmp / "t.jar", mod_id="jei")
        first = support.artifact("jei", jar)
        second = support.artifact("jei", jar)
        self.assertEqual(pack.modset_hash(env, support.resolved_env(env, [first])),
                         pack.modset_hash(env, support.resolved_env(env, [second])))

    def test_index_can_be_read_back_from_the_flat_build_dir(self):
        _env, _resolved, result, _run_dir = self.build()
        from_disk = pack.read_index_file(result.index_path)
        self.assertEqual(from_disk, pack.read_index(result.mrpack_path))


class OverridesTest(unittest.TestCase):
    """Beyond ``mods/``: the options preset and the world seed an env is launched into."""

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = Path(self._tmp.name)

    def build(self, env, *, seed=None):
        jar = support.write_jar(self.tmp / "cache" / "jei.jar", mod_id="jei")
        resolved = support.resolved_env(env, [support.artifact("jei", jar)])
        return pack.build_pack(env, resolved, self.tmp / "runs" / "unit-run", seed=seed,
                               log=lambda *_: None)

    def test_the_options_preset_is_in_the_pack_and_in_its_build_dir(self):
        result = self.build(support.mk_env())
        self.assertEqual(result.extra_overrides, ["options.txt"])
        self.assertIn("overrides/options.txt", pack.listed_overrides(result.mrpack_path))
        staged = result.overrides_dir() / "options.txt"
        self.assertEqual(staged.read_text(encoding="utf-8"), options.render("1.21.1"))
        self.assertIn("onboardAccessibility:true", options.render())
        self.assertEqual(options.language_code("1.12.2"), "zh_CN")
        self.assertEqual(options.language_code("1.21.1"), "zh_cn")
        self.assertEqual(options.language_code(None), "zh_cn")
        self.assertIn("lang:zh_CN", options.render("1.12.2"))
        self.assertIn("lang:zh_cn", options.render("1.21.1"))

    def test_the_112_preset_carries_the_version_s_language_code(self):
        """The staged file, not just the renderer, spells 1.12.2's language code.

        The harness's pinyin scenarios assert on Chinese item names, and a wrong
        case silently leaves the client in ``en_us`` — see the ``lang`` bullet in
        ``lib/options.py`` for the two vanilla call sites that make the case
        load-bearing.
        """
        env = support.mk_env(mc="1.12.2")
        result = self.build(env)
        staged = (result.overrides_dir() / "options.txt").read_text(encoding="utf-8")
        self.assertIn("lang:zh_CN\n", staged)
        self.assertNotIn("zh_cn", staged)

    def test_the_preset_carries_no_key_line(self):
        """A ``key_*`` line in a version-less preset makes the client discard the whole file.

        ``Options.dataFix`` reads a preset without a ``version:`` line as data-version 0 and runs
        the legacy chain, where ``OptionsKeyLwjgl3Fix`` parses every ``key_*`` value as an LWJGL2
        key id (an integer). A modern binding with a modifier is ``key.keyboard.f:CONTROL``, so the
        parse throws, ``Options.load()`` catches it, and every option — ``lang`` included — stays
        at its default. Measured 2026-10-14; the JEI focus key it was pinning is JEI's own
        registered default anyway.
        """
        keys = options.keys()
        self.assertEqual(keys, ["onboardAccessibility", "lang"])
        for key in keys:
            self.assertFalse(key.startswith("key_"), key)
        text = options.render()
        self.assertNotIn("key.keyboard.", text)
        for line in text.splitlines():
            self.assertEqual(line.count(":"), 1, f"{line!r} must be one key:value pair")

    def test_an_env_without_a_world_stages_no_saves_tree(self):
        result = self.build(support.mk_env())
        self.assertIsNone(result.world)
        self.assertIsNone(result.world_digest)
        self.assertNotIn("saves", result.extra_overrides)
        self.assertEqual([name for name in pack.listed_overrides(result.mrpack_path)
                          if "/saves/" in name], [])

    def test_a_declared_world_is_staged_into_overrides_saves(self):
        env = support.mk_env(world={"name": "jechworld"})
        seed = support.seed_info(self.tmp / "seed")
        result = self.build(env, seed=seed)
        self.assertEqual(result.world, "jechworld")
        self.assertEqual(result.world_digest, seed.digest)
        self.assertEqual(result.extra_overrides, ["options.txt", "saves/jechworld"])
        staged = result.overrides_dir() / "saves" / "jechworld"
        self.assertTrue((staged / "level.dat").is_file())
        self.assertFalse((staged / "session.lock").exists())
        listed = pack.listed_overrides(result.mrpack_path)
        self.assertIn("overrides/saves/jechworld/level.dat", listed)
        self.assertIn("overrides/saves/jechworld/region/r.0.0.mca", listed)

    def test_a_declared_world_without_a_seed_is_an_error(self):
        env = support.mk_env(world={"name": "jechworld"})
        with self.assertRaises(DependencyError) as caught:
            self.build(env)
        self.assertIn("jt seed-world", str(caught.exception))

    def test_staging_a_world_replaces_a_previous_one(self):
        env = support.mk_env(world={"name": "jechworld"})
        first = support.seed_info(self.tmp / "seed-a")
        stale = self.tmp / "seed-old"
        (stale / "world").mkdir(parents=True)
        (stale / "world" / "old.mca").write_bytes(b"old")
        result = self.build(env, seed=support.seed_info(stale))
        self.assertTrue(result.overrides_dir().joinpath("saves", "jechworld", "old.mca").is_file())
        result = self.build(env, seed=first)
        self.assertFalse(result.overrides_dir().joinpath("saves", "jechworld", "old.mca").exists())

    def test_the_world_does_not_change_the_instance_reuse_hash(self):
        """The instance is keyed on the modset; the world is re-staged every run."""
        plain = support.mk_env()
        with_world = support.mk_env(world={"name": "jechworld"})
        seed = support.seed_info(self.tmp / "seed")
        jar = support.write_jar(self.tmp / "cache" / "jei.jar", mod_id="jei")
        artifacts = [support.artifact("jei", jar)]
        self.assertEqual(pack.modset_hash(plain, support.resolved_env(plain, artifacts)),
                         pack.modset_hash(with_world, support.resolved_env(with_world, artifacts)))
        result = self.build(with_world, seed=seed)
        self.assertEqual(result.pack_hash, pack.modset_hash(with_world, support.resolved_env(with_world, artifacts)))


if __name__ == "__main__":
    unittest.main()
