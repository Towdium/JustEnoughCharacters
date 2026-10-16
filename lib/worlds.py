"""World seeds: the superflat world an env is launched into, and its cache.

A client that stops at the main menu has not finished starting the mods: some
registration work only happens once a level exists. JEI 19.x runs most of its
plugin phases — including ``registerAdvancedSearch``, the phase that logs JECh's
``Registering JEI pinyin search storage`` — at level load, so an env asserting on
that marker needs a world, not a longer wait at the title screen.

The seed is built the boring, version-exact way: the **vanilla dedicated server**
of the env's own Minecraft version generates a small superflat world, which is
then cached under ``<JT_HOME>/cache/worlds/<mc>-<name>``:

* ``world/``    the world tree exactly as the game directory's ``saves/<name>`` needs it
* ``manifest.json``  per-file sha256 + size, a content digest over them, and where
  the seed came from (server version, jar sha1, url)

The digest is what makes the cache honest: ``jt seed-world`` reuses a cached seed
only after re-hashing the tree and matching the manifest, so a truncated or
hand-edited world is rebuilt instead of staged. A rebuild is never automatic
twice over: ``jt run`` builds a missing seed once, and every later run — and
every other env naming the same (mc, world) pair — reuses it.

The digest identifies **this** build, not a cross-machine constant. The world
*shape* is fixed (flat, a pinned seed), but the files are not byte-identical
between builds: ``level.dat`` carries the generation timestamps and each region
chunk carries a last-update stamp, so two builds of the same (mc, world) differ
by a few bytes. Nothing depends on cross-machine equality — the cache is local
and the manifest describes the tree that is actually on disk.

The server is run in a scratch directory under the cache, writes ``seed-server.log``
there (kept only long enough to report a failure), and is asked to stop over its
own stdin once it prints ``Done (``. Nothing here uses the user's launcher, their
saves, or their Minecraft installation.
"""

from __future__ import annotations

import shutil
import socket
import subprocess
import tempfile
import time
from pathlib import Path

from . import mojang, paths
from .errors import DependencyError, JtError
from .util import atomic_write_json, load_json, read_text_lossy, sha1_file, sha256_file, sha256_json

SEED_SCHEMA = 1
MANIFEST_NAME = "manifest.json"
WORLD_DIRNAME = "world"
SERVER_LOG_NAME = "seed-server.log"

UNSTABLE_FILES = ("session.lock",)

SERVER_START_TIMEOUT_SEC = 300
SERVER_STOP_TIMEOUT_SEC = 60
SERVER_POLL_SEC = 0.5
SERVER_MEM_MB = 1024
FLAT_SEED = "jechcompat"

SERVER_PROPERTIES = (
    "level-name={level_name}\n"
    "level-type=flat\n"
    "level-seed={seed}\n"
    "spawn-protection=0\n"
    "online-mode=false\n"
    "max-players=1\n"
    "view-distance=4\n"
    "simulation-distance=4\n"
    "difficulty=peaceful\n"
    "gamemode=survival\n"
    "hardcore=false\n"
    "enable-status=false\n"
    "network-compression-threshold=-1\n"
    "sync-chunk-writes=false\n"
    "server-ip=127.0.0.1\n"
    "server-port={port}\n"
    "motd=jeCh compatibility test seed\n"
)
DONE_MARKER = "]: Done ("


class SeedInfo:
    """One cached world seed: where it is, what it hashes to, where it came from."""

    def __init__(self, *, mc, name, digest, directory, files, bytes_total, source,
                 generated_at, reused=False):
        self.mc = mc
        self.name = name
        self.digest = digest
        self.directory = Path(directory)
        self.files = list(files)
        self.bytes_total = bytes_total
        self.source = dict(source or {})
        self.generated_at = generated_at
        self.reused = bool(reused)

    @property
    def world_dir(self):
        return self.directory / WORLD_DIRNAME

    def to_json(self):
        return {
            "mc": self.mc,
            "name": self.name,
            "digest": self.digest,
            "files": len(self.files),
            "bytes": self.bytes_total,
            "source": self.source,
            "generatedAt": self.generated_at,
            "reused": self.reused,
            "path": paths.display(self.directory),
        }

    def describe(self):
        return (f"{self.name} ({len(self.files)} file(s), {self.bytes_total} bytes, "
                f"sha256 {self.digest[:16]}, {self.source.get('server', '?')})")

    def __repr__(self):
        return f"SeedInfo({self.mc}/{self.name!r}, {self.digest[:12]})"




def seed_key(env):
    """The cache directory one (mc, world name) pair maps to, shared across envs."""
    return f"{env.mc}-{env.world.name}"


def seed_dir(env):
    return paths.worlds_cache_dir() / seed_key(env)


def manifest_path(env):
    return seed_dir(env) / MANIFEST_NAME


def world_tree(env):
    return seed_dir(env) / WORLD_DIRNAME


def file_manifest(root):
    """``[{path, size, sha256}]`` for every file under ``root``, sorted by path.

    Paths are relative and posix; ``UNSTABLE_FILES`` are left out because their
    content is a session artefact, not part of what the seed *is*.
    """
    root = Path(root)
    entries = []
    if not root.is_dir():
        return entries
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.is_symlink():
            continue
        relative = path.relative_to(root).as_posix()
        if path.name in UNSTABLE_FILES:
            continue
        entries.append({"path": relative, "size": path.stat().st_size,
                        "sha256": sha256_file(path)})
    return entries


def manifest_digest(mc, name, files):
    return sha256_json({"schema": SEED_SCHEMA, "mc": mc, "name": name, "files": files})


def read_manifest(env):
    """The recorded manifest, or ``None`` when there is none / it is unusable."""
    path = manifest_path(env)
    if not path.is_file():
        return None
    try:
        manifest = load_json(path, what=f"world seed manifest {path.name}")
    except JtError:
        return None
    if not isinstance(manifest, dict) or not isinstance(manifest.get("files"), list):
        return None
    if manifest.get("digest") != manifest_digest(env.mc, manifest.get("name") or env.world.name,
                                                 manifest["files"]):
        return None
    return manifest


def verify_seed(mc, name, directory):
    """Re-hash a cached tree and match it against its manifest; ``SeedInfo`` or ``None``.

    Re-hashing on every lookup is the honest way to reuse a cache: a half-copied
    or hand-edited seed describes a different world than the one its manifest
    claims, and that must trigger a rebuild rather than a run.
    """
    directory = Path(directory)
    manifest_file = directory / MANIFEST_NAME
    tree = directory / WORLD_DIRNAME
    if not manifest_file.is_file() or not tree.is_dir():
        return None
    try:
        manifest = load_json(manifest_file, what=f"world seed manifest {manifest_file.name}")
    except JtError:
        return None
    if not isinstance(manifest, dict) or not isinstance(manifest.get("files"), list):
        return None
    files = file_manifest(tree)
    digest = manifest_digest(mc, name, files)
    if digest != manifest.get("digest"):
        return None
    return SeedInfo(
        mc=mc, name=manifest.get("name") or name, digest=digest, directory=directory,
        files=files, bytes_total=sum(entry["size"] for entry in files),
        source=manifest.get("source") or {}, generated_at=manifest.get("generatedAt"),
        reused=True,
    )


def cached_seed(env):
    return verify_seed(env.mc, env.world.name, seed_dir(env))




def ensure_seed(env, *, java_home, refresh=False, log=print):
    """The env's world seed, building it with the vanilla server when needed.

    ``java_home`` is a path or a zero-argument callable returning one; a cache hit
    never resolves it, so reusing a verified seed costs no JDK lookup at all.
    """
    if env.world is None:
        raise DependencyError(
            f"{env.name}: the env declares no world, so there is nothing to seed "
            '(add "world": {"name": "<folder>"} to the env file)'
        )
    if not refresh:
        cached = cached_seed(env)
        if cached is not None:
            log(f"world seed {env.name}: reused {cached.describe()} at "
                f"{paths.display(cached.directory)}")
            return cached
        if manifest_path(env).is_file():
            log(f"world seed {env.name}: the cached seed does not match its manifest; rebuilding")
    return build_seed(env, java_home=java_home, log=log)


def build_seed(env, *, java_home, log=print):
    """Generate the world with the vanilla server for ``env.mc`` and cache it."""
    paths.ensure_data_dirs()
    home = Path(java_home() if callable(java_home) else java_home) if java_home is not None else None
    if home is None:
        raise DependencyError(
            f"world seed {env.name}: no JDK {env.java_version} to start the vanilla "
            f"{env.mc} server with"
        )
    jar = mojang.ensure_server_jar(env.mc, log=log)
    java = home / "bin" / "java"
    if not java.is_file():
        raise DependencyError(
            f"world seed {env.name}: no java executable at {paths.display(java)}; "
            f"the vanilla server {env.mc} cannot be started without JDK {env.java_version}"
        )
    scratch = Path(tempfile.mkdtemp(prefix=f"{seed_key(env)}.", suffix=".tmp",
                                    dir=str(paths.worlds_cache_dir())))
    log(f"world seed {env.name}: generating with the vanilla {env.mc} server in "
        f"{paths.display(scratch)}")
    try:
        _run_server(env, scratch, java=java, jar=jar, log=log)
        generated = scratch / WORLD_DIRNAME
        if not (generated / "level.dat").is_file():
            raise DependencyError(
                f"world seed {env.name}: the server finished but wrote no world/level.dat "
                f"under {paths.display(scratch)}"
            )
        return _store_seed(env, generated, jar, log=log)
    finally:
        shutil.rmtree(scratch, ignore_errors=True)


def _run_server(env, scratch, *, java, jar, log):
    """Start the server, wait for ``Done (``, then ask it to stop and wait for it."""
    scratch = Path(scratch)
    (scratch / "eula.txt").write_text(
        "# accepted by jt: the server only ever generates a local test world\n"
        "eula=true\n",
        encoding="utf-8",
    )
    (scratch / "server.properties").write_text(
        SERVER_PROPERTIES.format(level_name=WORLD_DIRNAME, seed=FLAT_SEED, port=_free_port()),
        encoding="utf-8",
    )
    log_path = scratch / SERVER_LOG_NAME
    command = [str(java), f"-Xmx{SERVER_MEM_MB}M", f"-Xms{max(SERVER_MEM_MB // 2, 256)}M",
               "-jar", str(jar), "nogui"]
    with open(log_path, "wb") as handle:
        proc = subprocess.Popen(command, cwd=str(scratch), stdin=subprocess.PIPE,
                                stdout=handle, stderr=subprocess.STDOUT)
    try:
        _await_done(env, proc, log_path)
        _stop_server(env, proc, log_path, log=log)
    finally:
        if proc.poll() is None:
            proc.kill()
            try:
                proc.wait(timeout=30)
            except subprocess.TimeoutExpired:  # pragma: no cover - kill is final
                pass


def _await_done(env, proc, log_path):
    deadline = time.time() + SERVER_START_TIMEOUT_SEC
    while True:
        text = read_text_lossy(log_path)
        if DONE_MARKER in text:
            return
        if proc.poll() is not None:
            raise DependencyError(
                f"world seed {env.name}: the vanilla {env.mc} server exited with code "
                f"{proc.returncode} before it finished. Its last lines:\n"
                + _tail(log_path)
            )
        if time.time() >= deadline:
            proc.kill()
            raise DependencyError(
                f"world seed {env.name}: the vanilla {env.mc} server did not reach "
                f"{DONE_MARKER!r} within {SERVER_START_TIMEOUT_SEC}s. Its last lines:\n"
                + _tail(log_path)
            )
        time.sleep(SERVER_POLL_SEC)


def _stop_server(env, proc, log_path, *, log):
    """Ask the server to stop through its stdin and wait for it to flush the world."""
    if proc.stdin is not None:
        try:
            proc.stdin.write(b"stop\n")
            proc.stdin.flush()
        except OSError:
            pass
        finally:
            try:
                proc.stdin.close()
            except OSError:
                pass
    deadline = time.time() + SERVER_STOP_TIMEOUT_SEC
    while proc.poll() is None and time.time() < deadline:
        time.sleep(SERVER_POLL_SEC)
    if proc.poll() is None:
        log(f"world seed {env.name}: the server ignored 'stop'; killing it")
        proc.kill()
        try:
            proc.wait(timeout=30)
        except subprocess.TimeoutExpired:  # pragma: no cover - kill is final
            pass
    elif proc.returncode != 0:
        log(f"world seed {env.name}: the server exited with code {proc.returncode}; "
            "the world files it flushed are still usable")
    log(f"world seed {env.name}: server log kept at {paths.display(log_path)}")


def _store_seed(env, generated, jar, *, log):
    """Copy the generated world into the cache next to a fresh manifest."""
    directory = seed_dir(env)
    if directory.exists():
        shutil.rmtree(directory)
    (directory / WORLD_DIRNAME).parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(generated, directory / WORLD_DIRNAME, symlinks=True,
                    ignore=shutil.ignore_patterns(*UNSTABLE_FILES))
    files = file_manifest(directory / WORLD_DIRNAME)
    digest = manifest_digest(env.mc, env.world.name, files)
    source = {
        "server": f"vanilla-{env.mc}",
        "jar": paths.display(jar),
        "jarSha1": _jar_sha1(jar),
        "levelType": "flat",
        "seed": FLAT_SEED,
    }
    manifest = {
        "schema": SEED_SCHEMA,
        "mc": env.mc,
        "name": env.world.name,
        "digest": digest,
        "files": files,
        "source": source,
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z", time.localtime()),
    }
    atomic_write_json(directory / MANIFEST_NAME, manifest)
    info = SeedInfo(mc=env.mc, name=env.world.name, digest=digest, directory=directory,
                    files=files, bytes_total=sum(entry["size"] for entry in files),
                    source=source, generated_at=manifest["generatedAt"])
    log(f"world seed {env.name}: built {info.describe()}")
    log(f"world seed {env.name}: cache {paths.display(directory)}")
    return info


def describe(env, seed):
    """The report's one-line summary of the seed a run was launched into."""
    return {
        "name": env.world.name,
        "savesPath": env.world.saves_path,
        "digest": seed.digest,
        "files": len(seed.files),
        "bytes": seed.bytes_total,
        "source": seed.source.get("server"),
        "reused": seed.reused,
    }




def stage_world(source_world, dest_world):
    """Copy a seed's ``world/`` to ``dest`` (a fresh ``saves/<name>``), without locks."""
    source_world = Path(source_world)
    dest_world = Path(dest_world)
    if not source_world.is_dir():
        raise DependencyError(
            f"world seed: {paths.display(source_world)} is not a directory; rebuild it "
            "with jt seed-world --refresh"
        )
    if dest_world.exists():
        shutil.rmtree(dest_world)
    dest_world.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(source_world, dest_world, symlinks=True,
                    ignore=shutil.ignore_patterns(*UNSTABLE_FILES))
    return dest_world




def _free_port():
    """An ephemeral local port for the scratch server; nothing ever connects."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def _jar_sha1(jar):
    try:
        return sha1_file(jar)
    except OSError:  # pragma: no cover - the jar was just downloaded
        return None


def _tail(path, lines=12):
    text = read_text_lossy(path)
    tail = [line for line in text.strip().splitlines() if line.strip()][-lines:]
    return "\n".join("    " + line for line in tail) or "    (the server log is empty)"
