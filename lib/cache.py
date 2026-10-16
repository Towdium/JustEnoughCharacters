"""The layered, shared cache a direct launch is assembled from.

Everything a run needs to boot a client that is *not* a mod download lives under
``<JT_HOME>/cache``, one directory per thing that changes independently, so a
second row of the same Minecraft version (or the same loader, or the same
subject) reuses what the first one fetched:

``cache/mc/<mc>/``
    ``client.jar``, ``libraries/<maven path>``, ``native-jars/<maven path>``,
    ``natives/<os-arch>/`` (extracted), ``assets/indexes/<id>.json``,
    ``assets/objects/<ab>/<hash>``, ``logging/<id>.xml``.
``cache/loader/<type>-<version>-<mc>/``
    the loader's own install: ``libraries/<maven path>`` plus the launch profile
    it came from (``profile.json``), and ``remap-<side>-<client sha1>/`` — the
    loader's own remap state (see :func:`loader_remap_dir`), which is what the
    run's ``<game dir>/.fabric`` is a link to.
``cache/subject/<modid>-<sha1>/``
    the jar under test, copied out of the checkout so a rebuild during a run
    cannot change what the run tested.
``cache/http/``, ``cache/mods/``
    unchanged: API responses and the pinned mod jars (see ``lib.net``).

Nothing here decides *what* to download — that is ``lib/launch/*`` and
``lib/subject.py`` — this module owns only where it lands, and says out loud
whether a file was reused or fetched, because "the second run hit the cache" is
one of the things a run has to be able to show.
"""

from __future__ import annotations

from pathlib import Path

from . import paths


def ensure_dir(path) -> Path:
    """``path`` as a directory that exists (parents included)."""
    path = Path(path)
    path.mkdir(parents=True, exist_ok=True)
    return path




def mc_dir(mc) -> Path:
    return paths.cache_dir() / "mc" / str(mc)


def client_jar(mc) -> Path:
    return mc_dir(mc) / "client.jar"


def libraries_dir(mc) -> Path:
    """Vanilla libraries, by their own ``group/artifact/version`` path."""
    return mc_dir(mc) / "libraries"


def native_jars_dir(mc) -> Path:
    """The natives jars of a version that still ships them as classifiers."""
    return mc_dir(mc) / "native-jars"


def natives_dir(mc, platform_tag) -> Path:
    """Where a version's natives are extracted, per os-arch pair."""
    return mc_dir(mc) / "natives" / platform_tag


def assets_dir(mc) -> Path:
    """``assets/`` as ``--assetsDir`` expects it (``indexes/`` + ``objects/``)."""
    return mc_dir(mc) / "assets"


def logging_dir(mc) -> Path:
    return mc_dir(mc) / "logging"




def loader_dir(loader_type, loader_version, mc) -> Path:
    return paths.cache_dir() / "loader" / f"{loader_type}-{loader_version}-{mc}"


def loader_libraries_dir(loader_type, loader_version, mc) -> Path:
    return loader_dir(loader_type, loader_version, mc) / "libraries"


def loader_profile_path(loader_type, loader_version, mc) -> Path:
    return loader_dir(loader_type, loader_version, mc) / "profile.json"


def loader_hashes_path(loader_type, loader_version, mc) -> Path:
    """The sha1s of loader files that publish none of their own.

    Fabric's meta profile carries a hash for its asm dependencies but not for
    ``intermediary`` or the loader itself. Those are still worth verifying, so
    the first download records what it got and every later run checks it — the
    same discipline ``lib.net`` applies to the mods it caches.
    """
    return loader_dir(loader_type, loader_version, mc) / "sha1.json"


def subject_dir(modid, sha1) -> Path:
    return paths.cache_dir() / "subject" / f"{modid}-{sha1}"




def loader_remap_dir(loader_type, loader_version, mc, client_sha1, side="client") -> Path:
    """Where one client jar's *loader remap state* is kept, shared by every run.

    A production Fabric Loader remaps the game jar and every mod into
    ``<game dir>/.fabric`` on first launch — ``remappedJars/<mc>-<loader>/
    client-intermediary.jar`` (~25 MiB) and ``processedMods/``. Nothing about
    that work depends on the run, so it belongs in the cache like everything
    else, and ``<game dir>/.fabric`` is made a link to this directory instead of
    a fresh copy per run.

    The key carries the client jar's sha1 on purpose. Loader's own directory name
    (``minecraft-<mc>-<loader>``) has no hash of its input in it, so it would
    happily keep a remap of a client jar that has been replaced; a sha1 in the
    path is the guard loader does not have. ``side`` is ``client`` today, and is
    part of the name because a server remap is a different output.
    """
    return loader_dir(loader_type, loader_version, mc) / f"remap-{side}-{str(client_sha1)[:12]}"




def note(kind, path, *, log=print, cached=True):
    """One uniform line per cache decision: ``cache: <kind> hit: <path>``."""
    log(f"cache: {kind} {'hit' if cached else 'fetch'}: {paths.display(path)}")
