"""World seeds: manifests, cache verification, staging, and the server recipe."""

from __future__ import annotations

import contextlib
import io
import json
import os
import stat
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from lib import envs, mojang, paths, worlds
from lib.errors import DependencyError

from . import support


class ManifestTest(unittest.TestCase):
    def setUp(self):
        self._home = support.temp_home()
        self.home = self._home.__enter__()
        self.addCleanup(self._home.__exit__, None, None, None)
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = Path(self._tmp.name)
        self.env = support.mk_env(world={"name": "jechworld"})

    def test_the_digest_is_stable_and_content_sensitive(self):
        files = worlds.file_manifest(support.write_world_seed(self.env).world_dir)
        again = worlds.file_manifest(support.write_world_seed(self.env).world_dir)
        self.assertEqual(files, again, "the manifest must not depend on mtimes")
        self.assertEqual(worlds.manifest_digest("1.21.1", "w", files),
                         worlds.manifest_digest("1.21.1", "w", again))
        self.assertNotEqual(worlds.manifest_digest("1.21.1", "w", files),
                            worlds.manifest_digest("1.20.1", "w", files))
        self.assertNotEqual(worlds.manifest_digest("1.21.1", "w", files),
                            worlds.manifest_digest("1.21.1", "other", files))

    def test_the_session_lock_is_not_part_of_the_seed(self):
        seed = support.write_world_seed(self.env)
        names = [entry["path"] for entry in seed.files]
        self.assertIn("level.dat", names)
        self.assertNotIn("session.lock", names)

    def test_a_tampered_tree_does_not_verify(self):
        seed = support.write_world_seed(self.env)
        self.assertIsNotNone(worlds.verify_seed(seed.mc, seed.name, seed.directory))
        (seed.world_dir / "level.dat").write_bytes(b"someone edited this")
        self.assertIsNone(worlds.verify_seed(seed.mc, seed.name, seed.directory))
        self.assertIsNone(worlds.cached_seed(self.env))

    def test_a_missing_manifest_does_not_verify(self):
        seed = support.write_world_seed(self.env)
        (seed.directory / worlds.MANIFEST_NAME).unlink()
        self.assertIsNone(worlds.verify_seed(seed.mc, seed.name, seed.directory))

    def test_staging_replaces_the_destination_and_drops_the_lock(self):
        seed = support.write_world_seed(self.env)
        dest = self.tmp / "saves" / "jechworld"
        (dest / "stale").mkdir(parents=True)
        (dest / "stale" / "old.mca").write_bytes(b"old world")
        worlds.stage_world(seed.world_dir, dest)
        self.assertTrue((dest / "level.dat").is_file())
        self.assertFalse((dest / "stale").exists(), "the staged world replaces what was there")
        self.assertFalse((dest / "session.lock").exists())

    def test_staging_a_missing_seed_is_an_error(self):
        with self.assertRaises(DependencyError):
            worlds.stage_world(self.tmp / "nope", self.tmp / "out")

    def test_the_reported_shape_carries_the_digest(self):
        seed = support.write_world_seed(self.env)
        payload = worlds.describe(self.env, seed)
        self.assertEqual(payload["name"], "jechworld")
        self.assertEqual(payload["savesPath"], "saves/jechworld")
        self.assertEqual(payload["digest"], seed.digest)
        self.assertEqual(payload["source"], "vanilla-1.21.1")
        self.assertIn(seed.digest[:12], seed.describe())


class EnsureSeedTest(unittest.TestCase):
    def setUp(self):
        self._home = support.temp_home()
        self.home = self._home.__enter__()
        self.addCleanup(self._home.__exit__, None, None, None)
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = Path(self._tmp.name)

    def test_a_verified_cache_is_reused_without_building(self):
        env = support.mk_env(world={"name": "jechworld"})
        cached = support.write_world_seed(env)
        with mock.patch.object(worlds, "build_seed") as build:
            seed = worlds.ensure_seed(env, java_home=self.tmp, log=lambda *_: None)
        build.assert_not_called()
        self.assertTrue(seed.reused)
        self.assertEqual(seed.digest, cached.digest)

    def test_a_cache_hit_never_resolves_the_jdk(self):
        env = support.mk_env(world={"name": "jechworld"})
        support.write_world_seed(env)

        def explorer(version):  # pragma: no cover - a cache hit must not call this
            raise AssertionError("the JDK must not be looked up for a cached seed")

        seed = worlds.ensure_seed(env, java_home=lambda: explorer(env.java_version),
                                  log=lambda *_: None)
        self.assertTrue(seed.reused)

    def test_building_without_a_jdk_is_an_explicit_error(self):
        env = support.mk_env(world={"name": "jechworld"})
        with self.assertRaises(DependencyError) as caught:
            worlds.ensure_seed(env, java_home=None, log=lambda *_: None)
        self.assertIn("no JDK 21", str(caught.exception))

    def test_refresh_rebuilds_even_a_verified_cache(self):
        env = support.mk_env(world={"name": "jechworld"})
        support.write_world_seed(env)
        with mock.patch.object(worlds, "build_seed", return_value="built") as build:
            self.assertEqual(worlds.ensure_seed(env, java_home=self.tmp, refresh=True,
                                                log=lambda *_: None), "built")
        build.assert_called_once()

    def test_a_missing_cache_builds_once(self):
        env = support.mk_env(world={"name": "jechworld"})
        with mock.patch.object(worlds, "build_seed", return_value="built") as build:
            self.assertEqual(worlds.ensure_seed(env, java_home=self.tmp, log=lambda *_: None),
                             "built")
        build.assert_called_once()

    def test_an_env_without_a_world_cannot_be_seeded(self):
        with self.assertRaises(DependencyError) as caught:
            worlds.ensure_seed(support.mk_env(), java_home=self.tmp, log=lambda *_: None)
        self.assertIn("declares no world", str(caught.exception))

    def test_the_cache_key_is_shared_by_environments_of_one_version(self):
        first = support.mk_env(name="a", world={"name": "jechworld"})
        second = support.mk_env(name="b", world={"name": "jechworld"})
        self.assertEqual(worlds.seed_dir(first), worlds.seed_dir(second))
        self.assertEqual(worlds.seed_dir(first), paths.worlds_cache_dir() / "1.21.1-jechworld")
        other = support.mk_env(name="c", mc="1.20.1", world={"name": "jechworld"})
        self.assertNotEqual(worlds.seed_dir(first), worlds.seed_dir(other))


class MojangMetadataTest(unittest.TestCase):
    class FakeHttp:
        def __init__(self, bodies):
            self.bodies = bodies
            self.asked = []

        def get_json(self, url, **kwargs):
            self.asked.append(url)
            if url not in self.bodies:
                raise AssertionError(f"unexpected url {url}")
            return self.bodies[url]

    def test_the_server_download_is_read_from_the_version_json(self):
        version_url = "https://piston-meta.example/1.21.1.json"
        http = self.FakeHttp({
            mojang.VERSION_MANIFEST_URL: {"versions": [
                {"id": "1.21.1", "url": version_url},
                {"id": "1.20.1", "url": "https://piston-meta.example/1.20.1.json"},
            ]},
            version_url: {"downloads": {"server": {"url": "https://cdn.example/server.jar",
                                                   "sha1": "a" * 40, "size": 52_000_000}}},
        })
        info = mojang.server_download("1.21.1", http)
        self.assertEqual(info["url"], "https://cdn.example/server.jar")
        self.assertEqual(info["sha1"], "a" * 40)
        self.assertEqual(info["size"], 52_000_000)
        self.assertEqual(http.asked[0], mojang.VERSION_MANIFEST_URL)

    def test_an_unknown_version_lists_what_the_manifest_has(self):
        http = self.FakeHttp({mojang.VERSION_MANIFEST_URL: {"versions": [
            {"id": "1.21.1", "url": "u1"}, {"id": "1.20.1", "url": "u2"}]}})
        with self.assertRaises(DependencyError) as caught:
            mojang.server_download("9.9.9", http)
        self.assertIn("no such Minecraft version", str(caught.exception))
        self.assertIn("1.21.1", str(caught.exception))

    def test_a_version_without_a_server_jar_says_so(self):
        http = self.FakeHttp({
            mojang.VERSION_MANIFEST_URL: {"versions": [{"id": "1.21.1", "url": "u"}]},
            "u": {"downloads": {"client": {"url": "c"}}},
        })
        with self.assertRaises(DependencyError) as caught:
            mojang.server_download("1.21.1", http)
        self.assertIn("no standalone server jar", str(caught.exception))

    def test_the_jar_is_cached_per_version(self):
        with support.temp_home() as home:
            self.assertEqual(mojang.server_jar_path("1.21.1"), home / "cache" / "servers" / "server-1.21.1.jar")


class ServerRunTest(unittest.TestCase):
    """The seed builder drives a real (fake) ``java`` through its start/stop handshake."""

    def setUp(self):
        self._home = support.temp_home()
        self.home = self._home.__enter__()
        self.addCleanup(self._home.__exit__, None, None, None)
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tmp = Path(self._tmp.name)
        self.env = support.mk_env(world={"name": "jechworld"})

    def fake_java(self, body):
        home = self.tmp / f"jdk-{abs(hash(body))}"
        (home / "bin").mkdir(parents=True, exist_ok=True)
        script = home / "bin" / "java"
        script.write_text("#!/bin/sh\n" + body, encoding="utf-8")
        script.chmod(script.stat().st_mode | stat.S_IXUSR)
        return home

    def run_build(self, *, body, expect_error=False, timeout=worlds.SERVER_START_TIMEOUT_SEC):
        java_home = self.fake_java(body)
        jar = self.tmp / "server-1.21.1.jar"
        jar.write_bytes(b"not really a jar")
        with mock.patch.object(mojang, "ensure_server_jar", return_value=jar), \
                mock.patch.object(worlds, "SERVER_START_TIMEOUT_SEC", timeout), \
                mock.patch.object(worlds, "SERVER_POLL_SEC", 0.01):
            if expect_error:
                with self.assertRaises(DependencyError) as caught:
                    worlds.build_seed(self.env, java_home=java_home, log=lambda *_: None)
                return str(caught.exception)
            return worlds.build_seed(self.env, java_home=java_home, log=lambda *_: None)

    def test_the_world_the_server_wrote_is_cached_with_a_manifest(self):
        capture = self.tmp / "capture"
        capture.mkdir()
        with mock.patch.dict(os.environ, {"SEED_CAPTURE": str(capture)}):
            seed = self.run_build(body=(
                'mkdir -p world\n'
                'printf "level" > world/level.dat\n'
                'printf "region" > world/r.0.0.mca\n'
                '/bin/cp server.properties eula.txt "$SEED_CAPTURE"/\n'
                # /bin/echo, not the shell builtin: a builtin's output sits in sh's
                # block buffer and would not be visible in the log file until sh exits.
                '/bin/echo "[00:00:00] [Server thread/INFO]: Done (0.1s)! For help, type \\"help\\""\n'
                'read line\n'
            ))
        self.assertTrue(seed.world_dir.joinpath("level.dat").is_file())
        self.assertTrue(worlds.manifest_path(self.env).is_file())
        self.assertEqual(worlds.cached_seed(self.env).digest, seed.digest)
        self.assertEqual(seed.source["server"], "vanilla-1.21.1")
        self.assertEqual(seed.source["levelType"], "flat")
        # the scratch directory is not left behind
        strays = [p.name for p in paths.worlds_cache_dir().iterdir() if p.name.endswith(".tmp")]
        self.assertEqual(strays, [])
        # the scratch server was given an accepted eula and a flat, tiny world
        properties = (capture / "server.properties").read_text(encoding="utf-8")
        self.assertIn("level-type=flat", properties)
        self.assertIn("level-name=world", properties)
        self.assertIn("view-distance=4", properties)
        self.assertNotIn("online-mode=true", properties)
        self.assertIn("server-ip=127.0.0.1", properties)
        ports = [line for line in properties.splitlines() if line.startswith("server-port=")]
        self.assertEqual(len(ports), 1)
        self.assertNotEqual(ports[0], "server-port=0")
        self.assertIn("eula=true", (capture / "eula.txt").read_text(encoding="utf-8"))

    def test_a_server_that_exits_early_is_an_error_with_its_log(self):
        message = self.run_build(body='/bin/echo "boom: no eula"\nexit 7\n', expect_error=True)
        self.assertIn("exited with code 7", message)
        self.assertIn("boom: no eula", message)

    def test_a_server_that_never_finishes_is_bounded(self):
        # Long enough for the shell to start and write (a shebang exec takes a
        # moment), short enough to keep the test quick.
        message = self.run_build(body='/bin/echo "starting"\nsleep 30\n', expect_error=True,
                                 timeout=2.0)
        self.assertIn("did not reach", message)
        self.assertIn("starting", message)

    def test_a_server_that_writes_no_world_is_an_error(self):
        message = self.run_build(body=(
            '/bin/echo "[00:00:00] [Server thread/INFO]: Done (0.1s)! For help, type \\"help\\""\n'
        ), expect_error=True)
        self.assertIn("no world/level.dat", message)

    def test_a_missing_jdk_is_reported_before_the_server_starts(self):
        with mock.patch.object(mojang, "ensure_server_jar", return_value=self.tmp / "j.jar"):
            with self.assertRaises(DependencyError) as caught:
                worlds.build_seed(self.env, java_home=self.tmp / "no-jdk", log=lambda *_: None)
        self.assertIn("no java executable", str(caught.exception))

    def test_the_seed_key_names_the_version_and_the_world(self):
        self.assertEqual(worlds.seed_key(self.env), "1.21.1-jechworld")


class SeedWorldCliTest(unittest.TestCase):
    """``jt seed-world`` end to end, with the cache doing the work (no network, no server)."""

    def cli(self, argv):
        buffer = io.StringIO()
        from lib.__main__ import main
        with contextlib.redirect_stdout(buffer), contextlib.redirect_stderr(buffer):
            code = main(argv)
        return code, buffer.getvalue()

    def test_a_verified_cache_is_reused_and_reported(self):
        with support.temp_home():
            env = envs.find_env("moderner-26.1.2-fabric")
            seed = support.write_world_seed(env)
            code, output = self.cli(["seed-world", "moderner-26.1.2-fabric"])
        self.assertEqual(code, 0)
        self.assertIn("reused", output)
        self.assertIn(seed.digest[:16], output)
        self.assertIn("vanilla-26.1.2", output)

    def test_a_selection_where_nothing_needs_a_seed_fails_loudly(self):
        with support.temp_home():
            code, output = self.cli(["seed-world", "1.12.2-forge-hei"])
        self.assertEqual(code, 1)
        self.assertIn("declares no world, so there is nothing to seed", output)
        self.assertIn("none of the 1 selected env(s) declares a world", output)
        self.assertIn("never reported as a pass", output)

    def test_a_mixed_selection_seeds_what_needs_it_and_exits_zero(self):
        with support.temp_home():
            env = envs.find_env("moderner-26.1.2-fabric")
            seed = support.write_world_seed(env)
            code, output = self.cli(["seed-world", "1.12.2-forge-hei,moderner-26.1.2-fabric"])
        self.assertEqual(code, 0)
        self.assertIn(seed.digest[:16], output)
        self.assertIn("declares no world", output)

    def test_a_skipped_env_is_still_seedable_when_it_declares_a_world(self):
        """``skip`` stops execution, not cache maintenance."""
        with support.temp_home():
            env = envs.find_env("mv-1.20.1-forge")
            seed = support.write_world_seed(env)
            code, output = self.cli(["seed-world", "mv-1.20.1-forge"])
        self.assertEqual(code, 0)
        self.assertIn(seed.digest[:16], output)

    def test_envs_lists_the_world_and_the_skip(self):
        with support.temp_home():
            code, output = self.cli(["envs"])
        self.assertEqual(code, 0)
        self.assertIn("world: jechworld", output)
        self.assertIn("uitest: tag:smoke,tag:jei", output)
        # `mv-1.19.2-forge` cannot load a level before 1.20, so its storage
        # marker is unreachable, and the four multiple-version forge rows lost
        # their only launch path when the instance launcher was removed
        # (2026-10-14; see the matrix test in test_envs.py). A skip is a
        # first-class result, so `jt envs` prints it.
        self.assertIn("skip:", output)

    def test_envs_json_carries_the_skip_and_the_world(self):
        with support.temp_home():
            code, output = self.cli(["envs", "--json"])
        self.assertEqual(code, 0)
        listed = {item["name"]: item for item in json.loads(output)}
        # `moderner-26.1.2-fabric` is the current example of a world-declaring,
        # no-skip row (mv-1.20.1-fabric, the earlier example, joined the
        # standalone harness rows on 2026-10-15 and dropped its world with the
        # quick-play launch).
        self.assertIsNone(listed["moderner-26.1.2-fabric"]["skip"])
        self.assertEqual(listed["moderner-26.1.2-fabric"]["world"]["savesPath"], "saves/jechworld")
        # Both 1.21.1 rows are harness rows now (the standalone builds since
        # 2026-10-15): neither declares a world, because the level asserted
        # against is the one the in-client runner creates and pins itself, and
        # both carry the scenario selection instead. The fabric row also pins
        # JEI's focusSearch binding in the preset (JEI 19.x on fabric registers
        # no usable default), which is why its uitest block carries options.
        self.assertIsNone(listed["1.21.1-fabric"]["world"])
        self.assertIsNone(listed["1.21.1-fabric"]["readyWhen"])
        self.assertEqual(listed["1.21.1-fabric"]["uitest"],
                         {"selection": "tag:smoke,tag:jei",
                          "project": "uitest/1.21.1-fabric",
                          "options": {"version": "3955",
                                      "key_key.jei.focusSearch": "key.keyboard.f"}})
        self.assertIsNone(listed["1.21.1-neoforge"]["world"])
        self.assertEqual(listed["1.21.1-neoforge"]["uitest"],
                         {"selection": "tag:smoke,tag:jei",
                          "project": "uitest/1.21.1-neoforge"})
        # mv-1.19.2-forge is the row that cannot load a level before 1.20, so it
        # declares the storage marker it cannot reach and names what is missing
        # instead of pretending to run; the four multiple-version forge rows lost
        # their only launch path when the instance launcher was removed
        # (2026-10-14), so `jt envs --json` reports four skipped rows. The two
        # 1.12.2 rows used to be in this list and are not any more: the checkout
        # now boots a 1.12.2 client on this arm64 machine and runs the harness on
        # it — see envs/README.md, "The 1.12.2 rows and the gradle launch".
        self.assertEqual(
            sorted(name for name, item in listed.items() if item["skip"]),
            ["mv-1.16.5-forge", "mv-1.18.2-forge", "mv-1.19.2-forge", "mv-1.20.1-forge"])
        self.assertIn("quickPlay needs 1.20+", listed["mv-1.19.2-forge"]["skip"])
        # The revived 1.12.2 rows report the harness selection they run and the
        # level the harness creates for them, not a world of their own.
        self.assertEqual(listed["1.12.2-forge-jei-legacy"]["uitest"],
                         {"selection": "tag:smoke,tag:jei"})
        self.assertEqual(listed["1.12.2-forge-hei"]["uitest"], {"selection": "tag:smoke"})
        for name in ("1.12.2-forge-jei-legacy", "1.12.2-forge-hei"):
            self.assertIsNone(listed[name]["skip"])
            self.assertIsNone(listed[name]["world"], "the harness's own level is the world")
        self.assertIsNone(listed["mv-1.19.2-forge"]["world"])
        self.assertIsNone(listed["mv-1.19.2-fabric"]["skip"])
        self.assertIsNone(listed["mv-1.19.2-fabric"]["world"])


class TempHomeIsolationTest(unittest.TestCase):
    def test_seeds_live_under_the_temp_home(self):
        with support.temp_home() as home:
            env = support.mk_env(world={"name": "jechworld"})
            support.write_world_seed(env)
            self.assertTrue((home / "cache" / "worlds" / "1.21.1-jechworld" / "world").is_dir())
            self.assertNotEqual(str(home), os.path.expanduser("~"))


if __name__ == "__main__":
    unittest.main()
