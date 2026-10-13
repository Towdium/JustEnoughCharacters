"""Mojang's version metadata: the vanilla server jar for one Minecraft version.

``jt seed-world`` generates a world with the *vanilla* dedicated server, so it
needs the server jar that belongs to the env's Minecraft version. Mojang
publishes that in two hops, both of which are plain json:

1. ``version_manifest_v2.json`` — one entry per release, each carrying the url of
   its own version json;
2. that version json's ``downloads.server`` — the url, sha1 and size of the
   server jar.

The lookup is strict: an unknown version id or a version without a server
download is an error that lists what the manifest does offer, never a guess, and
the jar is cached under ``<JT_HOME>/cache/servers`` keyed by version and verified
by sha1, so a seed is built from a file whose integrity was checked.
"""

from __future__ import annotations

from . import paths
from .errors import DependencyError
from .net import BROWSER_USER_AGENT, HttpClient

VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
# A server jar is tens of megabytes; the http cache keeps only json, so this is
# the one download that goes straight to disk.
SERVER_JAR_TEMPLATE = "server-{mc}.jar"


def version_manifest(http=None, *, refresh=False):
    client = http or HttpClient(refresh=refresh)
    manifest = client.get_json(VERSION_MANIFEST_URL, what="Mojang version manifest")
    versions = manifest.get("versions") if isinstance(manifest, dict) else None
    if not isinstance(versions, list) or not versions:
        raise DependencyError(
            f"{VERSION_MANIFEST_URL}: no version list in the manifest "
            "(Mojang changed its shape, or the response was truncated)"
        )
    return versions


def version_entry(mc, http=None, *, refresh=False):
    """The manifest entry for one version id, or an error listing the closest."""
    versions = version_manifest(http, refresh=refresh)
    for entry in versions:
        if isinstance(entry, dict) and entry.get("id") == mc:
            return entry
    known = [str(entry.get("id")) for entry in versions if isinstance(entry, dict)]
    raise DependencyError(
        f"{mc}: no such Minecraft version in Mojang's version manifest "
        f"(the manifest lists {len(known)} versions, newest first: {', '.join(known[:5])} ...)"
    )


def version_json(mc, http=None, *, refresh=False):
    entry = version_entry(mc, http, refresh=refresh)
    url = entry.get("url")
    if not url:
        raise DependencyError(f"{mc}: the version manifest entry carries no url")
    body = (http or HttpClient(refresh=refresh)).get_json(url, what=f"Minecraft {mc} version json")
    if not isinstance(body, dict):
        raise DependencyError(f"{mc}: {url} did not return a json object")
    return body


def server_download(mc, http=None, *, refresh=False):
    """``{url, sha1, size}`` for the vanilla server jar of one Minecraft version."""
    body = version_json(mc, http, refresh=refresh)
    downloads = body.get("downloads")
    server = downloads.get("server") if isinstance(downloads, dict) else None
    if not isinstance(server, dict) or not server.get("url"):
        available = ", ".join(sorted(downloads)) if isinstance(downloads, dict) else "(none)"
        raise DependencyError(
            f"{mc}: this version publishes no standalone server jar "
            f"(downloads keys: {available}); a world seed cannot be built from it"
        )
    return {
        "url": server["url"],
        "sha1": server.get("sha1"),
        "size": server.get("size") or 0,
    }


def server_jar_path(mc):
    return paths.servers_cache_dir() / SERVER_JAR_TEMPLATE.format(mc=mc)


def ensure_server_jar(mc, *, http=None, refresh=False, log=print):
    """Download (or reuse) the vanilla server jar for ``mc``; returns its path."""
    client = http or HttpClient(refresh=refresh)
    info = server_download(mc, client, refresh=refresh)
    dest = server_jar_path(mc)
    client.download(info["url"], dest, headers={"User-Agent": BROWSER_USER_AGENT},
                    sha1=info["sha1"], what=f"vanilla server {mc}", refresh=refresh)
    size = dest.stat().st_size if dest.is_file() else 0
    if info["size"] and size != info["size"]:
        # Not fatal (the download is sha1-verified), but a size mismatch means the
        # metadata and the file disagree, which is worth saying out loud.
        log(f"vanilla server {mc}: {size} bytes on disk, {info['size']} bytes in the metadata")
    log(f"vanilla server {mc}: {paths.display(dest)} ({size} bytes, sha1 {info['sha1']})")
    return dest
