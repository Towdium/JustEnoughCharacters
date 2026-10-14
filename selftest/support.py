"""Shared fixtures for the selftests: temp JT_HOME, env builders, fake jars."""

from __future__ import annotations

import contextlib
import json
import os
import tempfile
import zipfile
from pathlib import Path

from lib import envs, resolve, worlds
from lib.util import sha1_file


@contextlib.contextmanager
def temp_home():
    """Point ``JT_HOME`` at a throwaway directory for the duration of a test."""
    previous = os.environ.get("JT_HOME")
    with tempfile.TemporaryDirectory(prefix="jt-selftest-") as tmp:
        os.environ["JT_HOME"] = tmp
        try:
            yield Path(tmp)
        finally:
            if previous is None:
                os.environ.pop("JT_HOME", None)
            else:
                os.environ["JT_HOME"] = previous


def env_dict(**overrides):
    """A minimal, schema-valid env; ``key=None`` removes a key entirely."""
    data = {
        "name": "unit-env",
        "tags": ["unit"],
        "mc": "1.21.1",
        "loader": {"type": "neoforge", "version": "21.1.215"},
        "java": {"version": 21},
        "gradle": {"task": ":neoforge:runClient", "daemon_java": 21},
        "jech": {
            "source": "local-build",
            "project": "1.21.1",
            "path": "build/libs/jecharacters-*.jar",
            "expectModId": "jecharacters",
        },
        "mods": [
            {"name": "jei", "source": "curseforge", "slug": "jei",
             "describe": {"version": "19.37.0.362"}, "pin": None},
        ],
        "checks": [{"type": "log_contains", "value": "jecharacters", "name": "jech-loaded"}],
        "timeout_sec": 600,
    }
    for key, value in overrides.items():
        if value is None:
            data.pop(key, None)
        else:
            data[key] = value
    return data


def mk_env(path="<unit>", **overrides):
    return envs.parse_env(env_dict(**overrides), path)


def write_jar(path, *, mod_id=None, layout="mods.toml", payload="not a real mod\n"):
    """Write a tiny but structurally real jar for the metadata readers."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        if mod_id and layout == "mods.toml":
            archive.writestr("META-INF/mods.toml",
                             'modLoader="javafml"\n[[mods]]\nmodId="%s"\n' % mod_id)
        elif mod_id and layout == "neoforge":
            archive.writestr("META-INF/neoforge.mods.toml",
                             '[[mods]]\nmodId="%s"\n' % mod_id)
        elif mod_id and layout == "fabric":
            archive.writestr("fabric.mod.json",
                             json.dumps({"schemaVersion": 1, "id": mod_id, "version": "1"}))
        elif mod_id and layout == "mcmod":
            archive.writestr("mcmod.info", json.dumps([{"modid": mod_id, "name": "x"}]))
        archive.writestr("unit/payload.txt", payload)
    return path


def artifact(name, path, *, role="mod", source="curseforge", pin=None, sha1=None):
    path = Path(path)
    return resolve.ResolvedArtifact(
        role=role, name=name, source=source, filename=path.name, path=path,
        sha1=sha1 or (sha1_file(path) if path.is_file() else None),
        size=path.stat().st_size if path.is_file() else 0,
        pin=pin or {"fileId": 1, "filename": path.name},
    )


def resolved_env(env, artifacts):
    return resolve.ResolvedEnv(env, list(artifacts))


def seed_info(root, *, name="jechworld", mc="1.21.1", payload=b"level data", digest=None):
    """A :class:`worlds.SeedInfo` over a throwaway tree, for tests that only stage it."""
    directory = Path(root)
    tree = directory / worlds.WORLD_DIRNAME
    (tree / "region").mkdir(parents=True, exist_ok=True)
    (tree / "level.dat").write_bytes(payload)
    (tree / "region" / "r.0.0.mca").write_bytes(b"region data")
    (tree / "session.lock").write_bytes(b"\x00" * 8)
    files = worlds.file_manifest(tree)
    return worlds.SeedInfo(
        mc=mc, name=name, digest=digest or worlds.manifest_digest(mc, name, files),
        directory=directory, files=files,
        bytes_total=sum(entry["size"] for entry in files),
        source={"server": f"vanilla-{mc}", "levelType": "flat"},
        generated_at="2026-01-01T00:00:00+0000",
    )


def write_world_seed(env, *, payload=b"level data", extra_files=(), with_lock=True,
                     directory=None):
    """Materialize a *cached* world seed for ``env`` under ``<JT_HOME>/cache/worlds``.

    Writes exactly what ``worlds._store_seed`` writes (a ``world/`` tree plus a
    manifest whose digest covers it), so the fixture exercises the real cache
    verification instead of a stub. Returns the verified :class:`worlds.SeedInfo`.
    """
    directory = Path(directory) if directory is not None else worlds.seed_dir(env)
    tree = directory / worlds.WORLD_DIRNAME
    (tree / "region").mkdir(parents=True, exist_ok=True)
    (tree / "level.dat").write_bytes(payload)
    (tree / "region" / "r.0.0.mca").write_bytes(b"region data")
    for name, content in extra_files:
        (tree / name).write_bytes(content)
    if with_lock:
        (tree / "session.lock").write_bytes(b"\x00" * 8)
    files = worlds.file_manifest(tree)
    manifest = {
        "schema": worlds.SEED_SCHEMA,
        "mc": env.mc,
        "name": env.world.name,
        "digest": worlds.manifest_digest(env.mc, env.world.name, files),
        "files": files,
        "source": {"server": f"vanilla-{env.mc}", "levelType": "flat", "seed": "unit"},
        "generatedAt": "2026-01-01T00:00:00+0000",
    }
    (directory / worlds.MANIFEST_NAME).write_text(json.dumps(manifest), encoding="utf-8")
    info = worlds.verify_seed(env.mc, env.world.name, directory)
    assert info is not None, "the fixture must produce a verifiable seed"
    return info
