"""Shared fixtures for the selftests: temp JT_HOME, env builders, fake jars."""

from __future__ import annotations

import contextlib
import json
import os
import tempfile
import zipfile
from pathlib import Path

from lib import envs, resolve
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
        "jvm": {"max_mem_mb": 3072},
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


def instance_dir_with(root, instance_id, *, cfg=True):
    """A fake Prism instance directory."""
    directory = Path(root) / "instances" / instance_id
    (directory / "minecraft").mkdir(parents=True, exist_ok=True)
    if cfg:
        (directory / "instance.cfg").write_text(
            "InstanceType=OneSix\nname=%s\nJavaPath=old-java\nJvmArgs=-Xmx512m\n" % instance_id,
            encoding="utf-8",
        )
    return directory
