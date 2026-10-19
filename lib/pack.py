"""Rendering an env into a ``.mrpack`` (Modrinth pack format).

The pack is deliberately dumb: ``files`` is empty and *every* mod jar — the
downloaded ones and the JECh artifact under test — rides in ``overrides/mods/``.
Two reasons:

* nothing is ever re-resolved out of a CDN halfway through: the jar set is exactly
  what the cache already verified, so a run works offline from the cache;
* ``overrides`` is copied into the game directory verbatim, which is what lets the
  isolation step rebuild ``mods/`` from one authoritative source.

The same authoritative-input rule covers the two things a game directory needs
beyond its mods:

* ``overrides/options.txt`` — the minimal preset that keeps the accessibility
  onboarding screen out of the startup path (see ``lib.options``);
* ``overrides/saves/<name>/`` — the world seed an env is launched into, rendered
  from the cache ``jt seed-world`` maintains (see ``lib.worlds``).

A third kind rides along when an env declares it: ``env.uitest.files`` writes
game-directory files verbatim under ``overrides/``, which is how a mod whose own
config opens a screen the headless client cannot click starts from a neutralised
file — the job ``options.txt`` does for the onboarding page.

The build directory lives under ``runs/<id>/pack`` and the zip next to it, so a
run's exact input set survives with the report.
"""

from __future__ import annotations

import json
import os
import shutil
import zipfile
from pathlib import Path

from . import envs, options, worlds
from .errors import ConfigError, DependencyError
from .util import atomic_write_text, dump_json_text, load_json, sha256_json

INDEX_NAME = "modrinth.index.json"
FORMAT_VERSION = 1
OVERRIDES_DIR = "overrides"
MODS_SUBDIR = "mods"
INDEX_GAME = "minecraft"


class PackResult:
    def __init__(self, *, pack_dir, index_path, mrpack_path, pack_hash, mod_filenames, version_id,
                 dependencies, extra_overrides=(), world=None, world_digest=None):
        self.pack_dir = Path(pack_dir)
        self.index_path = Path(index_path)
        self.mrpack_path = Path(mrpack_path)
        self.pack_hash = pack_hash
        self.mod_filenames = list(mod_filenames)
        self.version_id = version_id
        self.dependencies = dict(dependencies)
        self.extra_overrides = list(extra_overrides)
        self.world = world
        self.world_digest = world_digest

    def mods_dir(self):
        return self.pack_dir / OVERRIDES_DIR / MODS_SUBDIR

    def overrides_dir(self):
        return self.pack_dir / OVERRIDES_DIR

    def __repr__(self):
        return f"PackResult({self.mrpack_path.name}, {len(self.mod_filenames)} mod(s))"


def dependency_key(loader_type):
    try:
        return envs.MRPACK_DEPENDENCY_KEYS[loader_type]
    except KeyError:
        raise ConfigError(
            f"loader {loader_type!r} cannot be expressed as an mrpack dependency "
            f"(known: {', '.join(sorted(envs.MRPACK_DEPENDENCY_KEYS))})"
        )


def dependencies_for(env):
    return {INDEX_GAME: env.mc, dependency_key(env.loader.type): env.loader.version}


def build_index(env, resolved, version_id=None):
    """The ``modrinth.index.json`` body. ``files`` stays empty on purpose.

    ``name`` labels the pack, so a caller that wants a different label than the
    env name passes it as ``version_id``.
    """
    label = version_id or env.name
    return {
        "formatVersion": FORMAT_VERSION,
        "game": INDEX_GAME,
        "versionId": label,
        "name": label,
        "summary": f"JECh compatibility test env {env.name} ({env.mod_label}, java {env.java_version})",
        "files": [],
        "dependencies": dependencies_for(env),
    }


def modset_hash(env, resolved):
    """A stable fingerprint of everything that decides what the game loads.

    Timestamps and run ids are excluded on purpose: the hash answers "is this the
    same mod set as last time", which is what the report's ``packHash`` records.
    """
    mods = sorted(
        ({"name": a.name, "filename": a.filename, "sha1": a.sha1 or "", "size": a.size}
         for a in resolved.artifacts),
        key=lambda item: (item["name"], item["filename"]),
    )
    return sha256_json({
        "mc": env.mc,
        "loader": {"type": env.loader.type, "version": env.loader.version},
        "java": env.java_version,
        "mods": mods,
    })


def build_pack(env, resolved, run_dir, *, version_id=None, seed=None, log=print):
    """Stage ``overrides/`` (mods, options.txt, the world seed) and zip the index."""
    run_dir = Path(run_dir)
    pack_dir = run_dir / "pack"
    mods_dir = pack_dir / OVERRIDES_DIR / MODS_SUBDIR
    if pack_dir.exists():
        shutil.rmtree(pack_dir)
    mods_dir.mkdir(parents=True, exist_ok=True)

    staged = []
    for artifact in resolved.staged:
        if artifact.path is None or not artifact.path.is_file():
            raise DependencyError(
                f"{artifact.name}: {artifact.filename} is not in the cache "
                "(run jt resolve first; jt run does it automatically)"
            )
        dest = mods_dir / artifact.filename
        if dest.exists():
            raise DependencyError(
                f"two artifacts stage to {artifact.filename!r}; rename one in the env "
                "(mods[].name is only a label, the file name comes from the pin)"
            )
        _link_or_copy(artifact.path, dest)
        staged.append(artifact.filename)
    for artifact in resolved.artifacts:
        if not artifact.staged:
            log(f"pack: {artifact.name} ({artifact.filename}) stays on the checkout's dev "
                "classpath (via=dev-classpath), not staged into overrides/mods")

    extra_overrides = stage_overrides(env, pack_dir, seed=seed, log=log)

    index = build_index(env, resolved, version_id=version_id)
    index_path = pack_dir / INDEX_NAME
    atomic_write_text(index_path, dump_json_text(index))

    mrpack_path = run_dir / f"{env.name}.mrpack"
    _write_zip(pack_dir, mrpack_path)

    return PackResult(
        pack_dir=pack_dir,
        index_path=index_path,
        mrpack_path=mrpack_path,
        pack_hash=modset_hash(env, resolved),
        mod_filenames=staged,
        version_id=index["versionId"],
        dependencies=index["dependencies"],
        extra_overrides=extra_overrides,
        world=env.world.name if env.world is not None else None,
        world_digest=seed.digest if (env.world is not None and seed is not None) else None,
    )


def stage_overrides(env, pack_dir, *, seed=None, log=print):
    """Write the non-mod overrides: ``options.txt``, ``env.uitest.files`` and, when declared, the world.

    Returns the pack-relative posix paths of what it staged, so the caller (and
    the report) knows exactly which extra inputs a run carried.
    """
    overrides_dir = Path(pack_dir) / OVERRIDES_DIR
    overrides_dir.mkdir(parents=True, exist_ok=True)
    staged = []

    options_path = overrides_dir / options.FILENAME
    extra_options = env.uitest.options if env.uitest is not None else None
    atomic_write_text(options_path, options.render(env.mc, extra=extra_options))
    staged.append(options.FILENAME)
    described = [f"{key}={value}" for key, value, _why in options.explain(env.mc)]
    described += [f"{key}={value}" for key, value in sorted((extra_options or {}).items())]
    log(f"pack: overrides/{options.FILENAME} preset ({', '.join(described)})")

    staged_files = env.uitest.files if env.uitest is not None else None
    for relative in sorted(staged_files or {}):
        target = overrides_dir / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        atomic_write_text(target, staged_files[relative])
        staged.append(relative)
    if staged_files:
        log(f"pack: overrides/ files ({', '.join(sorted(staged_files))})")

    if env.world is None:
        return staged
    if seed is None:
        raise DependencyError(
            f"{env.name}: the env is launched into the world {env.world.name!r} but no seed was "
            f"supplied; run jt seed-world {env.name} first (jt run builds it automatically)"
        )
    target = overrides_dir / env.world.saves_path
    worlds.stage_world(seed.world_dir, target)
    staged.append(env.world.saves_path)
    log(f"pack: overrides/{env.world.saves_path} <- seed {seed.digest[:16]} "
        f"({len(seed.files)} file(s), {seed.bytes_total} bytes)")
    return staged


def listed_overrides(mrpack_path):
    """Every non-mod entry the pack carries, as pack-relative posix paths."""
    with zipfile.ZipFile(mrpack_path) as archive:
        names = [name for name in archive.namelist()
                 if name.startswith(OVERRIDES_DIR + "/") and not name.endswith("/")
                 and not name.startswith(f"{OVERRIDES_DIR}/{MODS_SUBDIR}/")]
    return sorted(names)


def _link_or_copy(source, dest):
    """Stage a cache file without ever mutating it: hardlink when possible."""
    try:
        os.link(source, dest)
        return
    except OSError:
        pass
    shutil.copy2(source, dest)


def _write_zip(pack_dir, out_path):
    """Zip the pack: index deflated, jars stored (they are already compressed)."""
    out_path.parent.mkdir(parents=True, exist_ok=True)
    tmp = out_path.with_name(out_path.name + ".tmp")
    if tmp.exists():
        tmp.unlink()
    with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(pack_dir.rglob("*")):
            if not path.is_file():
                continue
            arcname = path.relative_to(pack_dir).as_posix()
            compress = zipfile.ZIP_STORED if arcname.endswith(".jar") else zipfile.ZIP_DEFLATED
            archive.write(path, arcname=arcname, compress_type=compress)
    os.replace(tmp, out_path)
    return out_path


def read_index(mrpack_path):
    """Read the index out of a rendered pack (used by the selftest and by verify)."""
    with zipfile.ZipFile(mrpack_path) as archive:
        with archive.open(INDEX_NAME) as handle:
            return json.loads(handle.read().decode("utf-8"))


def read_index_file(index_path):
    return load_json(index_path, what=f"pack index {Path(index_path).name}")


def listed_mods(mrpack_path):
    with zipfile.ZipFile(mrpack_path) as archive:
        return sorted(name for name in archive.namelist()
                      if name.startswith(f"{OVERRIDES_DIR}/{MODS_SUBDIR}/") and name.endswith(".jar"))
