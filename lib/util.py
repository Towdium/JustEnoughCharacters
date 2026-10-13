"""Small shared helpers: atomic writes, hashing, timestamps, jar metadata."""

from __future__ import annotations

import fnmatch
import hashlib
import json
import os
import re
import sys
import tempfile
import time
import zipfile
from pathlib import Path

from .errors import JtError

# ---------------------------------------------------------------------------
# atomic file IO
# ---------------------------------------------------------------------------


def atomic_write_bytes(path, data):
    """Write ``data`` to ``path`` via a sibling temp file plus ``os.replace``.

    A reader either sees the previous content or the complete new content; an
    interrupted write leaves only a temp file behind, never a half-written
    report the verification gate would then read.
    """
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=str(path.parent))
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(data)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp, path)
        tmp = None
    finally:
        if tmp is not None and os.path.exists(tmp):
            os.unlink(tmp)
    return path


def atomic_write_text(path, text):
    return atomic_write_bytes(path, text.encode("utf-8"))


def dump_json_text(obj):
    """Canonical json rendering: 2-space indent, unicode kept, trailing newline.

    deterministic key order is *not* imposed here — callers that need a stable
    hash sort their own data before dumping.
    """
    return json.dumps(obj, indent=2, ensure_ascii=False, sort_keys=False) + "\n"


def atomic_write_json(path, obj):
    return atomic_write_text(path, dump_json_text(obj))


def load_json(path, what=None):
    """Read a json file, raising a JtError that names the file on any problem."""
    label = what or str(path)
    try:
        text = Path(path).read_text(encoding="utf-8")
    except FileNotFoundError:
        raise JtError(f"{label}: file not found")
    except OSError as exc:
        raise JtError(f"{label}: cannot read ({exc})")
    try:
        return json.loads(text)
    except json.JSONDecodeError as exc:
        raise JtError(f"{label}: invalid json ({exc})")


def read_text_lossy(path, limit=None):
    """Read a possibly-huge, possibly-invalid-utf8 log without raising."""
    try:
        with open(path, "rb") as handle:
            data = handle.read() if limit is None else handle.read(limit)
    except FileNotFoundError:
        return ""
    except OSError:
        return ""
    return data.decode("utf-8", errors="replace")


# ---------------------------------------------------------------------------
# hashing
# ---------------------------------------------------------------------------


def sha1_file(path):
    return _hash_file(path, "sha1")


def sha256_file(path):
    return _hash_file(path, "sha256")


def _hash_file(path, algo):
    digest = hashlib.new(algo)
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_text(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def sha256_json(obj):
    """Hash a json-serialisable object with sorted keys — the stable-hash helper."""
    return sha256_text(json.dumps(obj, sort_keys=True, ensure_ascii=False, separators=(",", ":")))


# ---------------------------------------------------------------------------
# names and time
# ---------------------------------------------------------------------------


def now_stamp():
    return time.strftime("%Y%m%d-%H%M%S", time.localtime())


def now_iso():
    return time.strftime("%Y-%m-%dT%H:%M:%S%z", time.localtime())


def sanitize_name(name):
    """A filesystem-safe token for run ids derived from env names."""
    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "-", str(name)).strip("-")
    return cleaned or "run"


def human_seconds(seconds):
    return f"{seconds:.1f}s"


# ---------------------------------------------------------------------------
# matching helpers
# ---------------------------------------------------------------------------


def fold(text):
    return (text or "").strip().casefold()


def token_set(text):
    return {tok for tok in re.split(r"[^A-Za-z0-9.+_-]+", text or "") if tok}


def glob_match(value, pattern):
    return fnmatch.fnmatchcase(fold(value), fold(pattern))


# ---------------------------------------------------------------------------
# jar metadata
# ---------------------------------------------------------------------------

_MODS_TOML = ("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
_TOML_MOD_ID = re.compile(r"""modId\s*=\s*["']([^"']+)["']""")


def jar_mod_ids(path):
    """Best-effort extraction of the mod ids a jar declares.

    Handles the three metadata layouts we meet across the JECh version lines:
    ``META-INF/(neoforge.)mods.toml`` (Forge 1.13+/NeoForge), ``fabric.mod.json``
    and the 1.12.2-era ``mcmod.info``. Returns an empty set when nothing is
    found; callers decide whether that is fatal.
    """
    ids = set()
    try:
        with zipfile.ZipFile(path) as archive:
            names = set(archive.namelist())
            for entry in _MODS_TOML:
                if entry in names:
                    text = archive.read(entry).decode("utf-8", errors="replace")
                    ids.update(_TOML_MOD_ID.findall(text))
            if "fabric.mod.json" in names:
                try:
                    meta = json.loads(archive.read("fabric.mod.json").decode("utf-8", "replace"))
                except (ValueError, UnicodeDecodeError):
                    meta = {}
                if isinstance(meta, dict) and isinstance(meta.get("id"), str):
                    ids.add(meta["id"])
            if "mcmod.info" in names:
                try:
                    meta = json.loads(archive.read("mcmod.info").decode("utf-8", "replace"))
                except (ValueError, UnicodeDecodeError):
                    meta = None
                entries = meta if isinstance(meta, list) else [meta] if isinstance(meta, dict) else []
                for item in entries:
                    if isinstance(item, dict):
                        for key in ("modid", "modId"):
                            if isinstance(item.get(key), str):
                                ids.add(item[key])
    except (zipfile.BadZipFile, FileNotFoundError, OSError):
        return set()
    return {i for i in ids if i}


# ---------------------------------------------------------------------------
# process helpers
# ---------------------------------------------------------------------------


def eprint(*parts):
    print(*parts, file=sys.stderr)


def log_lines(prefix, message):
    return f"{prefix}: {message}"
