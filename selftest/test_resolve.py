"""Resolution: local-build globs, pin write-back, offline download reuse."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from lib import cfapi, envs, paths, resolve
from lib.errors import ConfigError, DependencyError
from lib.util import sha1_file

from . import support


class FakeCandidate:
    """Stands in for a cfapi.CfFile / mrapi.MrVersion."""

    def __init__(self, pin):
        self._pin = pin

    def pin(self):
        return dict(self._pin)


class FakeClient:
    def __init__(self, candidate):
        self.candidate = candidate
        self.calls = []

    def resolve(self, slug, mc, loader, version, *, what=None):
        self.calls.append((slug, mc, loader, version, what))
        return self.candidate


class BoomHttp:
    """Any use of it is a test failure: the offline paths must not touch http."""

    def __getattr__(self, item):
        raise AssertionError(f"http was used ({item}) on a path that must stay offline")


class RecordingHttp:
    def __init__(self):
        self.downloads = []
        self.downloaded_sha1 = None

    def download(self, url, dest, *, headers=None, sha1=None, what=None, refresh=False):
        self.downloads.append(url)
        self.downloaded_sha1 = sha1
        Path(dest).parent.mkdir(parents=True, exist_ok=True)
        Path(dest).write_bytes(b"downloaded")
        return Path(dest)

    def get_json(self, url, *, headers=None, what=None, **kwargs):
        return {"files": [{"primary": True, "url": "https://cdn.modrinth.com/from-api.jar",
                           "filename": "x.jar", "hashes": {"sha1": "cd" * 20}}]}


class LocalBuildTest(unittest.TestCase):
    def setUp(self):
        self._home = support.temp_home()
        self.home = self._home.__enter__()
        self.addCleanup(self._home.__exit__, None, None, None)
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = Path(self._tmp.name)
        self.line = self.tmp / "1.21.1"
        (self.line / "build" / "libs").mkdir(parents=True)
        self.jar = support.write_jar(self.line / "build" / "libs" / "jecharacters-1.21.1-forge-4.6.8.jar",
                                     mod_id="jecharacters")
        self._env = mock.patch.dict("os.environ", {"JT_JECH_ROOT": str(self.tmp)})
        self._env.start()
        self.addCleanup(self._env.stop)

    def test_the_glob_selects_the_single_runtime_jar(self):
        env = support.mk_env()
        self.assertEqual(resolve.local_build_jar(env), self.jar)

    def test_sources_jars_are_not_candidates(self):
        support.write_jar(self.line / "build" / "libs" / "jecharacters-1.21.1-forge-4.6.8-sources.jar",
                          mod_id="jecharacters")
        env = support.mk_env()
        self.assertEqual(resolve.local_build_jar(env), self.jar)

    def test_two_runtime_jars_are_ambiguous(self):
        support.write_jar(self.line / "build" / "libs" / "jecharacters-1.21.1-forge-4.6.9.jar",
                          mod_id="jecharacters")
        with self.assertRaises(DependencyError) as caught:
            resolve.local_build_jar(support.mk_env())
        self.assertIn("ambiguous", str(caught.exception))

    def test_the_search_order_is_project_scoped_then_root(self):
        env = support.mk_env(jech={"source": "local-build", "project": "moderner",
                                   "path": "build/libs/*.jar", "expectModId": "jecharacters"})
        self.assertEqual(resolve.search_roots(env, self.tmp), [self.tmp / "moderner", self.tmp])
        no_project = support.mk_env(jech={"source": "local-build", "path": "build/libs/*.jar",
                                         "expectModId": "jecharacters"})
        self.assertIsNone(no_project.jech.project)
        self.assertEqual(resolve.search_roots(no_project, self.tmp), [])

    def test_a_branch_checkout_root_is_used_when_the_project_dir_is_absent(self):
        """CI: the checkout root already is the version line, so the project name misses."""
        branch = self.tmp / "branch-checkout"
        jar = support.write_jar(branch / "neoforge" / "build" / "libs" / "jecharacters.jar",
                                mod_id="jecharacters")
        env = support.mk_env(jech={"source": "local-build", "project": "moderner",
                                   "path": "neoforge/build/libs/*.jar",
                                   "expectModId": "jecharacters"})
        with mock.patch.dict("os.environ", {"JT_JECH_ROOT": str(branch)}):
            self.assertEqual(resolve.local_build_jar(env), jar)

    def test_the_project_scoped_root_wins_over_a_root_level_match(self):
        support.write_jar(self.tmp / "build" / "libs" / "jecharacters-root.jar",
                          mod_id="jecharacters")
        env = support.mk_env(jech={"source": "local-build", "project": "1.21.1",
                                   "path": "build/libs/jecharacters-*.jar",
                                   "expectModId": "jecharacters"})
        self.assertEqual(resolve.local_build_jar(env), self.jar)

    def test_the_error_names_every_root_it_searched(self):
        with mock.patch.dict("os.environ", {"JT_JECH_ROOT": str(self.tmp / "empty")}):
            (self.tmp / "empty").mkdir()
            with self.assertRaises(DependencyError) as caught:
                resolve.local_build_jar(support.mk_env())
        message = str(caught.exception)
        self.assertIn("1.21.1", message)
        self.assertIn("empty", message)

    def test_nothing_matching_is_an_error_that_names_the_search_paths(self):
        with mock.patch.dict("os.environ", {"JT_JECH_ROOT": str(self.tmp / "empty")}):
            (self.tmp / "empty").mkdir()
            with self.assertRaises(DependencyError) as caught:
                resolve.local_build_jar(support.mk_env())
        self.assertIn("no jar matches", str(caught.exception))

    def test_an_absolute_path_is_rejected_by_the_schema(self):
        with self.assertRaises(ConfigError):
            support.mk_env(jech={"source": "local-build", "path": str(self.jar),
                                 "expectModId": "jecharacters"})

    def test_the_project_key_selects_one_checkout(self):
        other = self.tmp / "moderner"
        (other / "build" / "libs").mkdir(parents=True)
        support.write_jar(other / "build" / "libs" / "jecharacters-1.21.1-forge-4.6.8.jar",
                          mod_id="jecharacters")
        with_choice = support.mk_env(jech={"source": "local-build", "project": "moderner",
                                           "path": "build/libs/*.jar", "expectModId": "jecharacters"})
        self.assertEqual(resolve.local_build_jar(with_choice),
                         other / "build" / "libs" / "jecharacters-1.21.1-forge-4.6.8.jar")
        # without the project key both checkouts match, so the glob is ambiguous
        no_choice = support.mk_env(jech={"source": "local-build", "path": "build/libs/*.jar",
                                        "expectModId": "jecharacters"})
        with self.assertRaises(DependencyError) as caught:
            resolve.local_build_jar(no_choice)
        self.assertIn("ambiguous", str(caught.exception))

    def test_resolving_a_local_build_needs_no_http(self):
        env = support.mk_env(mods=[])
        with mock.patch.object(resolve, "HttpClient", return_value=BoomHttp()):
            resolved = resolve.resolve_env(env, download=True, log=lambda *_: None)
        jech = resolved.jech
        self.assertEqual(jech.source, "local-build")
        self.assertEqual(jech.path, self.jar)
        self.assertEqual(jech.sha1, sha1_file(self.jar))
        self.assertFalse(resolved.changed)

    def test_a_wrong_mod_id_in_the_jar_is_an_error(self):
        env = support.mk_env(jech={"source": "local-build", "project": "1.21.1",
                                   "path": "build/libs/*.jar", "expectModId": "somethingelse"})
        with self.assertRaises(DependencyError) as caught:
            resolve.resolve_env(env, download=False, log=lambda *_: None)
        self.assertIn("declares mod ids", str(caught.exception))

    def test_a_jar_without_metadata_is_a_note_not_a_failure(self):
        plain = self.line / "build" / "libs" / "jecharacters-1.21.1-forge-4.6.8-plain.jar"
        support.write_jar(plain)  # no mod metadata at all
        env = support.mk_env(mods=[], jech={"source": "local-build", "project": "1.21.1",
                                            "path": "build/libs/*-plain.jar",
                                            "expectModId": "jecharacters"})
        resolved = resolve.resolve_env(env, download=False, log=lambda *_: None)
        self.assertTrue(any("mod metadata" in note for note in resolved.notes))


class PinningTest(unittest.TestCase):
    def setUp(self):
        self._home = support.temp_home()
        self.home = self._home.__enter__()
        self.addCleanup(self._home.__exit__, None, None, None)
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = Path(self._tmp.name)
        self.line = self.tmp / "1.21.1"
        (self.line / "build" / "libs").mkdir(parents=True)
        support.write_jar(self.line / "build" / "libs" / "jecharacters.jar", mod_id="jecharacters")
        self._env = mock.patch.dict("os.environ", {"JT_JECH_ROOT": str(self.tmp)})
        self._env.start()
        self.addCleanup(self._env.stop)
        self.path = self.tmp / "unit.json"
        data = support.env_dict()
        data["jech"] = {"source": "local-build", "project": "1.21.1",
                        "path": "build/libs/jecharacters.jar", "expectModId": "jecharacters"}
        self.path.write_text(json.dumps(data), encoding="utf-8")
        self.candidate = FakeCandidate({"fileId": 4242, "filename": "jei-19.37.0.362.jar",
                                        "downloadUrl": "https://edge.forgecdn.net/files/4242/jei.jar",
                                        "sha1": "ab" * 20})

    def test_a_missing_pin_is_resolved_and_written_back(self):
        env = envs.load_env(self.path)
        client = FakeClient(self.candidate)
        with mock.patch.object(cfapi.CurseForgeClient, "from_env", return_value=client):
            resolved = resolve.resolve_env(env, download=False, log=lambda *_: None)
        self.assertTrue(resolved.changed)
        self.assertEqual(client.calls[0][:4], ("jei", "1.21.1", "neoforge", "19.37.0.362"))
        written = json.loads(self.path.read_text(encoding="utf-8"))
        self.assertEqual(written["mods"][0]["pin"]["fileId"], 4242)
        self.assertEqual(written["mods"][0]["pin"]["filename"], "jei-19.37.0.362.jar")
        self.assertEqual(written["mods"][0]["pin"]["sha1"], "ab" * 20)
        self.assertEqual([a.filename for a in resolved.mods], ["jei-19.37.0.362.jar"])

    def test_a_pinned_mod_is_skipped_so_resolution_is_idempotent(self):
        env = envs.load_env(self.path)
        with mock.patch.object(cfapi.CurseForgeClient, "from_env", return_value=FakeClient(self.candidate)):
            resolve.resolve_env(env, download=False, log=lambda *_: None)
        reloaded = envs.load_env(self.path)
        client = FakeClient(self.candidate)
        with mock.patch.object(cfapi.CurseForgeClient, "from_env", return_value=client):
            again = resolve.resolve_env(reloaded, download=False, log=lambda *_: None)
        self.assertEqual(client.calls, [], "a pinned mod must not be re-queried")
        self.assertFalse(again.changed)
        self.assertTrue(reloaded.mods[0].pinned)

    def test_a_remote_jech_is_pinned_like_a_mod(self):
        data = support.env_dict(mods=[])
        data["jech"] = {"source": "modrinth", "slug": "jecharacters",
                        "describe": {"version": "4.6.8"}, "pin": None}
        self.path.write_text(json.dumps(data), encoding="utf-8")
        env = envs.load_env(self.path)
        client = FakeClient(FakeCandidate({"versionId": "ver-9", "filename": "jech.jar",
                                           "downloadUrl": "https://cdn.modrinth.com/jech.jar"}))
        with mock.patch("lib.mrapi.ModrinthClient.from_env", return_value=client):
            resolved = resolve.resolve_env(env, download=False, log=lambda *_: None)
        self.assertTrue(resolved.changed)
        written = json.loads(self.path.read_text(encoding="utf-8"))
        self.assertEqual(written["jech"]["pin"]["versionId"], "ver-9")
        self.assertEqual(resolved.jech.filename, "jech.jar")

    def test_a_pin_without_a_filename_is_reported_clearly(self):
        with self.assertRaises(DependencyError):
            resolve._artifact_from_pin(role="mod", name="jei", source="curseforge",
                                       pin={"fileId": 1}, detail="x")


class DownloadTest(unittest.TestCase):
    def setUp(self):
        self._home = support.temp_home()
        self.home = self._home.__enter__()
        self.addCleanup(self._home.__exit__, None, None, None)

    def cached_artifact(self, filename="jei.jar", content=b"cached"):
        path = paths.mods_cache_dir() / filename
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return resolve.ResolvedArtifact(role="mod", name="jei", source="curseforge", filename=filename,
                                        path=path, sha1=sha1_file(path),
                                        pin={"fileId": 1, "filename": filename})

    def test_a_cached_file_is_reused_without_a_request(self):
        artifact = self.cached_artifact()
        resolve.ensure_download(artifact, http=BoomHttp(), log=lambda *_: None)
        self.assertEqual(artifact.sha1, sha1_file(artifact.path))

    def test_a_cached_download_never_hits_the_network(self):
        artifact = self.cached_artifact()
        with mock.patch.object(resolve, "HttpClient", return_value=BoomHttp()):
            resolve.ensure_download(artifact, http=BoomHttp(), log=lambda *_: None)
        self.assertTrue(artifact.path.is_file())

    def test_a_missing_file_is_downloaded_from_the_pinned_url(self):
        artifact = resolve.ResolvedArtifact(
            role="mod", name="jei", source="curseforge", filename="jei.jar",
            path=paths.mods_cache_dir() / "jei.jar",
            pin={"fileId": 4242, "filename": "jei.jar",
                 "downloadUrl": "https://edge.forgecdn.net/files/4242/jei.jar"},
        )
        http = RecordingHttp()
        resolve.ensure_download(artifact, http=http, log=lambda *_: None)
        self.assertEqual(http.downloads, ["https://edge.forgecdn.net/files/4242/jei.jar"])
        self.assertEqual(artifact.sha1, sha1_file(artifact.path))

    def test_the_cdn_layout_is_the_fallback_when_a_pin_has_no_url(self):
        artifact = resolve.ResolvedArtifact(
            role="mod", name="jei", source="curseforge", filename="jei.jar",
            path=paths.mods_cache_dir() / "jei.jar", pin={"fileId": 4242, "filename": "jei.jar"})
        urls = resolve.download_urls(artifact)
        self.assertEqual(urls, [cfapi.cdn_url(4242, "jei.jar")])

    def test_a_modrinth_pin_without_a_url_asks_the_version_endpoint(self):
        artifact = resolve.ResolvedArtifact(
            role="mod", name="jei", source="modrinth", filename="jei.jar",
            path=paths.mods_cache_dir() / "jei.jar", pin={"versionId": "ver-9", "filename": "jei.jar"})
        http = RecordingHttp()
        resolve.ensure_download(artifact, http=http, log=lambda *_: None)
        self.assertIn("https://cdn.modrinth.com/from-api.jar", http.downloads)

    def test_a_refresh_ignores_the_cached_copy(self):
        artifact = self.cached_artifact()
        http = RecordingHttp()
        resolve.ensure_download(artifact, http=http, refresh=True, log=lambda *_: None)
        self.assertEqual(len(http.downloads), 1)


if __name__ == "__main__":
    unittest.main()
