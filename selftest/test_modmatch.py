"""Candidate matching and the per-source pin shapes (no network)."""

from __future__ import annotations

import unittest

from lib import cfapi, modmatch, mrapi
from lib.errors import DependencyError


def cf_payload(file_id, filename, *, display=None, date="2026-01-01T00:00:00Z", release=1,
               sha1=None, download=True, versions=("1.21.1", "NeoForge")):
    data = {
        "id": file_id,
        "modId": 238222,
        "fileName": filename,
        "displayName": display or filename,
        "releaseType": release,
        "fileDate": date,
        "fileLength": 1234,
        "gameVersions": list(versions),
        "downloadUrl": f"https://edge.forgecdn.net/files/{file_id}/{filename}" if download else None,
        "hashes": ([{"value": sha1, "algo": cfapi.HASH_ALGO_SHA1}] if sha1 else []),
    }
    return data


MR_FILES = [
    {"hashes": {"sha1": "aa" * 20, "sha512": "bb" * 64}, "url": "https://cdn.modrinth.com/x.jar",
     "filename": "x.jar", "primary": False, "size": 11},
    {"hashes": {"sha1": "cc" * 20, "sha512": "dd" * 64}, "url": "https://cdn.modrinth.com/primary.jar",
     "filename": "primary.jar", "primary": True, "size": 22},
]


def mr_payload(version_id, version_number, *, published="2026-01-01T00:00:00Z", kind="release",
               files=None):
    return {
        "id": version_id,
        "project_id": "abc",
        "name": f"release {version_number}",
        "version_number": version_number,
        "version_type": kind,
        "loaders": ["neoforge"],
        "game_versions": ["1.21.1"],
        "date_published": published,
        "files": files if files is not None else MR_FILES,
    }


class ScoringTest(unittest.TestCase):
    def test_exact_filename_beats_a_substring(self):
        candidates = [
            cfapi.CfFile(cf_payload(1, "jei-1.21.1-neoforge-19.37.0.362.jar")),
            cfapi.CfFile(cf_payload(2, "jei-1.21.1-neoforge-19.37.0.362-api.jar")),
        ]
        chosen = modmatch.pick("jei-1.21.1-neoforge-19.37.0.362.jar", candidates,
                              what="jei", source="curseforge", description="x")
        self.assertEqual(chosen.file_id, 1)

    def test_a_version_string_matches_inside_a_filename(self):
        candidates = [cfapi.CfFile(cf_payload(7, "jei-1.21.1-neoforge-19.37.0.362.jar"))]
        chosen = modmatch.pick("19.37.0.362", candidates, what="jei", source="curseforge",
                               description="x")
        self.assertEqual(chosen.file_id, 7)

    def test_a_glob_matches(self):
        candidates = [cfapi.CfFile(cf_payload(9, "jecharacters-1.21.1-forge-4.6.8.jar"))]
        chosen = modmatch.pick("jecharacters-1.21.1-*.jar", candidates, what="jech",
                               source="curseforge", description="x")
        self.assertEqual(chosen.file_id, 9)

    def test_display_name_matches_when_the_filename_is_opaque(self):
        candidates = [cfapi.CfFile(cf_payload(3, "abc123.jar", display="19.37.0.362"))]
        chosen = modmatch.pick("19.37.0.362", candidates, what="jei", source="curseforge",
                               description="x")
        self.assertEqual(chosen.file_id, 3)

    def test_empty_description_takes_the_newest_release(self):
        candidates = [
            cfapi.CfFile(cf_payload(1, "old.jar", date="2025-01-01T00:00:00Z")),
            cfapi.CfFile(cf_payload(2, "new.jar", date="2026-01-01T00:00:00Z")),
            cfapi.CfFile(cf_payload(3, "beta.jar", date="2027-01-01T00:00:00Z", release=2)),
        ]
        chosen = modmatch.pick("", candidates, what="jei", source="curseforge", description="")
        self.assertEqual(chosen.file_id, 2)

    def test_no_match_lists_the_candidates(self):
        candidates = [cfapi.CfFile(cf_payload(1, "one.jar")), cfapi.CfFile(cf_payload(2, "two.jar"))]
        with self.assertRaises(DependencyError) as caught:
            modmatch.pick("nine.jar", candidates, what="jei", source="curseforge", description="x")
        message = str(caught.exception)
        self.assertIn("no curseforge file", message)
        self.assertIn("2 total", message)
        self.assertIn("one.jar", message)

    def test_ambiguity_lists_the_matches(self):
        candidates = [
            cfapi.CfFile(cf_payload(1, "mod-1.0-forge.jar")),
            cfapi.CfFile(cf_payload(2, "mod-1.0-fabric.jar")),
        ]
        with self.assertRaises(DependencyError) as caught:
            modmatch.pick("mod-1.0", candidates, what="jei", source="curseforge", description="x")
        self.assertIn("ambiguous", str(caught.exception))

    def test_no_candidates_at_all_is_an_error(self):
        with self.assertRaises(DependencyError):
            modmatch.pick("x", [], what="jei", source="curseforge", description="x")


class PinShapeTest(unittest.TestCase):
    def test_curseforge_pin_uses_the_download_url(self):
        candidate = cfapi.CfFile(cf_payload(1234567, "jei.jar", sha1="ab" * 20))
        pin = candidate.pin()
        self.assertEqual(pin["fileId"], 1234567)
        self.assertEqual(pin["filename"], "jei.jar")
        self.assertIn("downloadUrl", pin)
        self.assertEqual(pin["sha1"], "ab" * 20)

    def test_curseforge_cdn_fallback_when_the_author_hid_the_file(self):
        candidate = cfapi.CfFile(cf_payload(1234567, "jei.jar", download=False))
        self.assertIsNone(candidate.data["downloadUrl"])
        self.assertEqual(candidate.url(), cfapi.cdn_url(1234567, "jei.jar"))
        self.assertTrue(candidate.url().endswith("/files/1234/567/jei.jar"))
        self.assertEqual(candidate.pin()["downloadUrl"], candidate.url())

    def test_curseforge_loader_ids(self):
        self.assertEqual(cfapi.LOADER_TYPE_IDS["forge"], 1)
        self.assertEqual(cfapi.LOADER_TYPE_IDS["neoforge"], 6)
        self.assertEqual(cfapi.LOADER_TYPE_IDS["fabric"], 4)
        self.assertEqual(cfapi.LOADER_TYPE_IDS["quilt"], 5)

    def test_modrinth_pin_uses_the_primary_file_and_version_id(self):
        version = mrapi.MrVersion(mr_payload("ver-1", "19.37.0.362"))
        self.assertEqual(version.filename, "primary.jar")
        pin = version.pin()
        self.assertEqual(pin["versionId"], "ver-1")
        self.assertEqual(pin["filename"], "primary.jar")
        self.assertEqual(pin["downloadUrl"], "https://cdn.modrinth.com/primary.jar")
        self.assertEqual(pin["sha1"], "cc" * 20)
        self.assertEqual(pin["sha512"], "dd" * 64)

    def test_modrinth_user_agent_is_the_documented_one(self):
        self.assertEqual(mrapi.USER_AGENT, "jech-compat-tests/0.1 (github:vfyjxf)")

    def test_modrinth_loader_tags_are_identity_mapped(self):
        self.assertEqual(mrapi.LOADER_TAGS["neoforge"], "neoforge")
        self.assertEqual(mrapi.LOADER_TAGS["forge"], "forge")


if __name__ == "__main__":
    unittest.main()
