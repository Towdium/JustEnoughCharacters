"""Modrinth client (``https://api.modrinth.com/v2``).

Modrinth asks every client to identify itself, so all requests carry the
documented agent string. No credential is involved; nothing secret is read here.
"""

from __future__ import annotations

from . import modmatch
from .errors import DependencyError, HttpError
from .net import API_USER_AGENT, HttpClient, with_query

BASE_URL = "https://api.modrinth.com/v2"
USER_AGENT = "jech-compat-tests/0.1 (github:vfyjxf)"

# Modrinth's own loader tag names — identical to ours, spelled out so a rename of
# one side fails loudly here instead of quietly filtering everything away.
LOADER_TAGS = {"forge": "forge", "neoforge": "neoforge", "fabric": "fabric", "quilt": "quilt"}


class MrVersion:
    """One Modrinth version, normalised for modmatch."""

    def __init__(self, data, *, project=None):
        self.data = data
        self.version_id = data.get("id")
        self.project_id = data.get("project_id")
        self.name = data.get("name") or ""
        self.version_number = data.get("version_number") or ""
        self.version_type = data.get("version_type") or ""
        self.loaders = list(data.get("loaders") or [])
        self.game_versions = list(data.get("game_versions") or [])
        self.published = data.get("date_published") or ""
        self.project = project
        files = [f for f in (data.get("files") or []) if isinstance(f, dict)]
        primary = [f for f in files if f.get("primary")] or files
        self.file = primary[0] if primary else {}
        for extra in primary[1:]:
            if (extra.get("filename") or "").endswith(".jar") and not self.file.get("filename"):
                self.file = extra

    @property
    def filename(self):
        return self.file.get("filename") or ""

    @property
    def size(self):
        return self.file.get("size") or 0

    @property
    def url(self):
        return self.file.get("url") or ""

    def hash(self, algo):
        hashes = self.file.get("hashes") or {}
        return (hashes.get(algo) or "").lower() or None

    @property
    def sha1(self):
        return self.hash("sha1")

    @property
    def is_release(self):
        return self.version_type == "release"

    @property
    def newest_key(self):
        return (self.published, self.version_id or "")

    @property
    def label(self):
        return self.filename or f"versionId={self.version_id}"

    @property
    def match_fields(self):
        return [("filename", self.filename), ("version_number", self.version_number),
                ("name", self.name)]

    def describe(self):
        parts = [f"versionId={self.version_id} {self.filename or '?'}",
                 f"version_number={self.version_number!r}"]
        if self.published:
            parts.append(f"date={self.published}")
        if self.version_type:
            parts.append(self.version_type)
        return "  ".join(parts) + "  loader/game=" + ",".join(self.loaders + self.game_versions)

    def pin(self):
        pin = {"versionId": self.version_id, "filename": self.filename, "downloadUrl": self.url}
        if self.sha1:
            pin["sha1"] = self.sha1
        sha512 = self.hash("sha512")
        if sha512:
            pin["sha512"] = sha512
        if self.size:
            pin["size"] = self.size
        return pin


class ModrinthClient:
    def __init__(self, http=None, user_agent=USER_AGENT):
        self.http = http or HttpClient(user_agent=user_agent)

    @classmethod
    def from_env(cls, http=None):
        return cls(http=http)

    def project(self, slug):
        payload = self.http.get_json(f"{BASE_URL}/project/{slug}", what=f"modrinth project {slug}")
        if not isinstance(payload, dict) or "id" not in payload:
            raise DependencyError(f"Modrinth project {slug!r} returned no project record")
        return payload

    def versions(self, slug, mc, loader):
        """Every version of ``slug`` for this game version and loader."""
        if loader not in LOADER_TAGS:
            raise DependencyError(f"loader {loader!r} has no Modrinth loader tag")
        url = with_query(
            f"{BASE_URL}/project/{slug}/version",
            {"game_versions": f'["{mc}"]', "loaders": f'["{LOADER_TAGS[loader]}"]'},
        )
        try:
            payload = self.http.get_json(url, what=f"modrinth versions {slug} mc={mc} loader={loader}")
        except HttpError as exc:
            if exc.status == 404:
                raise DependencyError(f"Modrinth has no project with the slug {slug!r}")
            raise
        if not isinstance(payload, list):
            raise DependencyError(f"modrinth versions {slug}: unexpected response shape")
        versions = [MrVersion(item, project=slug) for item in payload if isinstance(item, dict)]
        if not versions:
            # The filtered endpoint is authoritative, but a mod that only tags its
            # loader differently (e.g. a Forge mod on 1.12.2 with no loader tag at
            # all) is still worth a look before declaring it missing.
            unfiltered = self.http.get_json(
                f"{BASE_URL}/project/{slug}/version", what=f"modrinth versions (unfiltered) {slug}")
            for item in unfiltered if isinstance(unfiltered, list) else []:
                candidate = MrVersion(item, project=slug)
                if mc in candidate.game_versions and (not candidate.loaders
                                                      or LOADER_TAGS[loader] in candidate.loaders):
                    versions.append(candidate)
        return versions

    def resolve(self, slug, mc, loader, describe_version, *, what=None):
        label = what or slug
        versions = self.versions(slug, mc, loader)
        chosen = modmatch.pick(
            describe_version, versions,
            what=f"{label} ({slug} on modrinth, mc={mc}, loader={loader})",
            source="modrinth",
            description=describe_version,
        )
        return chosen
