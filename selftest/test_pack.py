"""mrpack rendering: index shape, overrides-only layout, hashes."""

from __future__ import annotations

import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from lib import pack
from lib.errors import ConfigError, DependencyError

from . import support


class PackTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = Path(self._tmp.name)

    def build(self, *, env=None, jars=(("jei", "jei.jar"), ("jech", "jecharacters.jar"))):
        env = env or support.mk_env()
        artifacts = []
        for index, (name, filename) in enumerate(jars):
            jar = support.write_jar(self.tmp / "cache" / filename, mod_id=name)
            artifacts.append(support.artifact(name, jar, role="jech" if name == "jech" else "mod"))
        resolved = support.resolved_env(env, artifacts)
        run_dir = self.tmp / "runs" / "unit-run"
        result = pack.build_pack(env, resolved, run_dir, log=lambda *_: None)
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


if __name__ == "__main__":
    unittest.main()
