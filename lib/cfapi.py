"""CurseForge client (``https://api.curseforge.com/v1``).

The API key comes from ``$CF_API_KEY``; when that variable is unset, jt falls
back to ``<JT_HOME>/secrets.env`` (see ``lib.secrets``) so the key never has to
appear on a command line. Once read, jt never prints it, never writes it into a
cache entry and never accepts it as an argument, so a pasted console log or a
committed cache file cannot leak it.
"""

from __future__ import annotations

from . import modmatch, secrets
from .errors import DependencyError, HttpError, SecretMissingError
from .net import HttpClient, with_query

BASE_URL = "https://api.curseforge.com/v1"
GAME_ID_MINECRAFT = 432
CLASS_ID_MODS = 6
API_KEY_ENV = "CF_API_KEY"
CDN_HOST = "https://mediafilez.forgecdn.net"

LOADER_TYPE_IDS = {"forge": 1, "neoforge": 6, "fabric": 4, "quilt": 5}
LOADER_TAGS = {
    "forge": ("forge",),
    "neoforge": ("neoforge",),
    "fabric": ("fabric",),
    "quilt": ("quilt",),
}

HASH_ALGO_SHA1 = 1
HASH_ALGO_MD5 = 2

PAGE_SIZE = 50


# Both hosts serve the same layout; a file that 403s on one downloads from the
# other (mediafilez 403s for a handful of files, edge answered every time).
CDN_HOST = "https://edge.forgecdn.net"
CDN_HOST_ALT = "https://mediafilez.forgecdn.net"


def cdn_url(file_id, filename, host=CDN_HOST):
    """The public CDN link for a file id — used when a file has no downloadUrl.

    CurseForge hides ``downloadUrl`` for files whose author disabled third-party
    downloads; the CDN layout is public and stable, and the harness only ever
    fetches its own pinned files.
    """
    return f"{host}/files/{int(file_id) // 1000}/{int(file_id) % 1000:03d}/{filename}"


class CfFile:
    """One CurseForge file entry, normalised for modmatch."""

    def __init__(self, data, *, mod_name=None):
        self.data = data
        self.file_id = data.get("id")
        self.mod_id = data.get("modId")
        self.display_name = data.get("displayName") or ""
        self.filename = data.get("fileName") or ""
        self.download_url = data.get("downloadUrl")
        self.release_type = data.get("releaseType")  # 1 release, 2 beta, 3 alpha
        self.game_versions = list(data.get("gameVersions") or [])
        self.uploaded = data.get("fileDate") or ""
        self.size = data.get("fileLength") or 0
        self.mod_name = mod_name
        self.hashes = {h.get("algo"): (h.get("value") or "").lower()
                       for h in (data.get("hashes") or []) if isinstance(h, dict)}

    @property
    def sha1(self):
        return self.hashes.get(HASH_ALGO_SHA1) or None

    @property
    def is_release(self):
        return self.release_type == 1

    @property
    def newest_key(self):
        return (self.uploaded, self.file_id or 0)

    @property
    def label(self):
        return self.filename or f"fileId={self.file_id}"

    @property
    def match_fields(self):
        return [("fileName", self.filename), ("displayName", self.display_name)]

    def url(self):
        return self.download_url or cdn_url(self.file_id, self.filename)

    def describe(self):
        parts = [f"fileId={self.file_id} {self.filename or '?'}"]
        if self.display_name and self.display_name != self.filename:
            parts.append(f"name={self.display_name!r}")
        if self.uploaded:
            parts.append(f"date={self.uploaded}")
        kinds = {1: "release", 2: "beta", 3: "alpha"}.get(self.release_type)
        if kinds:
            parts.append(kinds)
        return "  ".join(parts) + "  loader/game=" + ",".join(self.game_versions)

    def pin(self):
        pin = {"fileId": self.file_id, "filename": self.filename, "downloadUrl": self.url()}
        if self.sha1:
            pin["sha1"] = self.sha1
        if self.size:
            pin["size"] = self.size
        return pin


class CurseForgeClient:
    def __init__(self, api_key, http=None):
        if not api_key or not str(api_key).strip():
            raise SecretMissingError(
                f"{API_KEY_ENV} is empty. jt reads the CurseForge key from the environment only "
                f"(export {API_KEY_ENV}=... or source your secrets.env); the value is never printed, "
                "logged or cached."
            )
        self._api_key = str(api_key).strip()
        self.http = http or HttpClient()

    @classmethod
    def from_env(cls, http=None):
        return cls(secrets.get(API_KEY_ENV), http=http)

    def _headers(self):
        return {"x-api-key": self._api_key}

    def _get(self, url, what):
        try:
            return self.http.get_json(url, headers=self._headers(), what=what)
        except HttpError as exc:
            if exc.status in (401, 403):
                raise SecretMissingError(
                    f"CurseForge rejected the key in {API_KEY_ENV} ({exc.status}). "
                    "Issue a fresh key at the CurseForge developer console; jt never echoes the value."
                )
            if exc.status == 404:
                raise DependencyError(f"{what}: not found on CurseForge (HTTP 404)")
            raise

    def search_mod(self, slug):
        """The mod record whose slug matches exactly."""
        url = with_query(f"{BASE_URL}/mods/search",
                         {"gameId": GAME_ID_MINECRAFT, "classId": CLASS_ID_MODS, "slug": slug})
        payload = self._get(url, f"curseforge search {slug}")
        data = payload.get("data") if isinstance(payload, dict) else None
        if not data:
            raise DependencyError(
                f"CurseForge has no mod with the exact slug {slug!r} "
                "(check the slug in the env, not the display name)"
            )
        exact = [m for m in data if (m.get("slug") or "").strip().casefold() == slug.strip().casefold()]
        if not exact:
            found = ", ".join(sorted(m.get("slug") or "?" for m in data))
            raise DependencyError(f"CurseForge slug {slug!r} did not match exactly; it returned: {found}")
        return exact[0]

    def list_files(self, mod_id, mc, loader):
        """Every file of ``mod_id`` for this game version and loader, newest first."""
        if loader not in LOADER_TYPE_IDS:
            raise DependencyError(f"loader {loader!r} has no CurseForge ModLoaderType")
        files = []
        index = 0
        while True:
            url = with_query(f"{BASE_URL}/mods/{mod_id}/files", {
                "gameVersion": mc,
                "modLoaderType": LOADER_TYPE_IDS[loader],
                "pageSize": PAGE_SIZE,
                "index": index,
            })
            payload = self._get(url, f"curseforge files mod={mod_id} mc={mc} loader={loader}")
            data = payload.get("data") or []
            files.extend(CfFile(item) for item in data if isinstance(item, dict))
            total = (payload.get("pagination") or {}).get("totalCount")
            index += len(data)
            if not data or total is None or index >= total:
                break
        if not files:
            files = self._list_files_unfiltered(mod_id, mc, loader)
        return files

    def _list_files_unfiltered(self, mod_id, mc, loader):
        tags = {t.casefold() for t in LOADER_TAGS.get(loader, ())}
        keep = []
        index = 0
        while True:
            url = with_query(f"{BASE_URL}/mods/{mod_id}/files",
                             {"gameVersion": mc, "pageSize": PAGE_SIZE, "index": index})
            payload = self._get(url, f"curseforge files (unfiltered) mod={mod_id} mc={mc}")
            data = payload.get("data") or []
            for item in data:
                candidate = CfFile(item)
                versions = {v.casefold() for v in candidate.game_versions}
                if mc.lower() in versions and tags & versions:
                    keep.append(candidate)
            total = (payload.get("pagination") or {}).get("totalCount")
            index += len(data)
            if not data or total is None or index >= total:
                break
        return keep

    def resolve(self, slug, mc, loader, describe_version, *, what=None):
        label = what or slug
        mod = self.search_mod(slug)
        files = self.list_files(mod.get("id"), mc, loader)
        chosen = modmatch.pick(
            describe_version, files,
            what=f"{label} ({slug} on curseforge, mc={mc}, loader={loader})",
            source="curseforge",
            description=describe_version,
        )
        chosen.mod_name = mod.get("name") or slug
        return chosen
