"""Where jt keeps its data.

Nothing here hard-codes a machine path. The single root is ``JT_HOME``, resolved
in this order:

1. ``$JT_HOME`` when it is set and non-empty;
2. the workspace data directory (the ``jech-compat`` folder under the workspace's
   ``Data`` folder) when it exists — the workstation layout, where one checkout
   shares its cache with its sibling projects. The path is *computed* from this
   file's location, never written down;
3. ``~/.jech-compat`` — the per-user fallback a CI runner gets, unless it sets
   ``$JT_HOME`` at its own cache directory.

The root holds::

    <JT_HOME>/
      cache/http/    cached API responses (one json per request)
      cache/mods/    downloaded mod jars, content addressed by filename + hash
      cache/servers/ downloaded vanilla server jars, one per Minecraft version
      cache/worlds/  generated world seeds: <mc>-<name>/world plus a manifest
      runs/          one directory per run: report.json, report.txt, junit.xml, pack/, logs
      secrets.env    machine-local secrets (only CF_API_KEY), never committed

Everything outside ``JT_HOME`` is derived from the location of this package:
the repository root is the ``compat-tests`` directory, its parent is the JECh
workspace checkout directory, and two levels up is the workspace root.
"""

from __future__ import annotations

import os
from pathlib import Path

JT_HOME_ENV = "JT_HOME"
DEFAULT_JT_HOME = "~/.jech-compat"
# The workspace data root, relative to the workspace root (see repo_data_home).
REPO_DATA_HOME_PARTS = ("Data", "jech-compat")
SECRETS_FILENAME = "secrets.env"


def repo_root() -> Path:
    """The ``compat-tests`` directory itself (also ``sys.path[0]`` for ``jt``)."""
    return Path(__file__).resolve().parent.parent


def jech_root() -> Path:
    """The directory holding the JECh version-line checkouts (``Project/JechGround``)."""
    return repo_root().parent


def workspace_root() -> Path:
    """The workspace root two levels above ``compat-tests`` (used for display only)."""
    return repo_root().parents[2]


def repo_data_home() -> Path:
    """The workspace data directory: the ``jech-compat`` folder under ``Data``.

    Computed from this file's location, never written as a literal. It exists on
    this workstation and does not in a CI checkout, which is exactly what the
    ``JT_HOME`` chain in :func:`default_jt_home` keys off.
    """
    return workspace_root().joinpath(*REPO_DATA_HOME_PARTS)


def default_jt_home() -> Path:
    """The root used when ``$JT_HOME`` is unset: the workspace data dir, else ``~``."""
    candidate = repo_data_home()
    try:
        if candidate.is_dir():
            return candidate
    except OSError:  # pragma: no cover - is_dir() is tolerant, but be safe
        pass
    return Path(os.path.expanduser(DEFAULT_JT_HOME))


def jt_home() -> Path:
    """The data root: ``$JT_HOME``, then the workspace data dir, then ``~/.jech-compat``."""
    raw = os.environ.get(JT_HOME_ENV)
    if raw and raw.strip():
        return Path(os.path.expanduser(raw.strip()))
    return default_jt_home()


def secrets_path() -> Path:
    """``<JT_HOME>/secrets.env`` — the machine-local, never-committed secret file."""
    return jt_home() / SECRETS_FILENAME


def cache_dir() -> Path:
    return jt_home() / "cache"


def http_cache_dir() -> Path:
    return cache_dir() / "http"


def mods_cache_dir() -> Path:
    return cache_dir() / "mods"


def servers_cache_dir() -> Path:
    """Vanilla server jars, one per Minecraft version (the world seeder needs one)."""
    return cache_dir() / "servers"


def worlds_cache_dir() -> Path:
    """Generated world seeds, one ``<mc>-<name>`` directory each (see ``lib.worlds``)."""
    return cache_dir() / "worlds"


def runs_dir() -> Path:
    return jt_home() / "runs"


def envs_dir() -> Path:
    return repo_root() / "envs"


def ensure_data_dirs() -> list:
    """Create the data skeleton on demand; returns the directories it ensured."""
    made = []
    for path in (cache_dir(), http_cache_dir(), mods_cache_dir(), servers_cache_dir(),
                 worlds_cache_dir(), runs_dir()):
        if not path.exists():
            path.mkdir(parents=True, exist_ok=True)
            made.append(path)
    return made


def display(path) -> str:
    """A short, workspace-relative rendering of a path for logs and reports.

    Falls back to the path unchanged when it lives outside the workspace, so this
    never fails and never invents a path. Both candidates are resolved first: on
    macOS the temporary directory is a symlink into its private counterpart, so
    comparing an unresolved root against a resolved path would never match.
    """
    try:
        resolved = Path(path).resolve()
    except OSError:  # pragma: no cover - resolve() is tolerant, but be safe
        return str(path)
    bases = []
    for base in (workspace_root(), jt_home()):
        try:
            bases.append(base.resolve())
        except OSError:  # pragma: no cover
            bases.append(base)
    for base in bases:
        try:
            return str(resolved.relative_to(base))
        except ValueError:
            continue
    return str(path)
