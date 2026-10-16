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
    it came from (``profile.json``).
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


# ---------------------------------------------------------------------------
# one Minecraft version
# ---------------------------------------------------------------------------


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


# ---------------------------------------------------------------------------
# one loader install, one subject jar
# ---------------------------------------------------------------------------


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


# ---------------------------------------------------------------------------
# telling a reader what happened
# ---------------------------------------------------------------------------


def note(kind, path, *, log=print, cached=True):
    """One uniform line per cache decision: ``cache: <kind> hit: <path>``."""
    log(f"cache: {kind} {'hit' if cached else 'fetch'}: {paths.display(path)}")
