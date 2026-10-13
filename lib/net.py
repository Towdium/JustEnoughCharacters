"""HTTP with an on-disk cache, shared by the CurseForge and Modrinth clients.

Two caches live under ``<JT_HOME>/cache``:

* ``cache/http/<sha256>.json`` — one cached json body per (url, request headers),
  carrying ``fetched_at`` so callers can apply a max age. ``--refresh`` bypasses
  the read and rewrites the entry.
* ``cache/mods/<filename>`` — downloaded jars. A file is reused when it is
  present, non-empty and (when a hash is known) matches it. Downloads land in a
  sibling ``.part`` file first and are moved into place only after verification,
  so an interrupted download can never be mistaken for a complete mod.

Nothing here ever writes a request header value into a cache file: the entry
holds the url, the timestamp and the parsed body only. That is what keeps the
CurseForge API key out of everything we persist.
"""

from __future__ import annotations

import hashlib
import json
import os
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from . import paths
from .errors import HttpError, NetworkError
from .util import dump_json_text, load_json, sha1_file

API_USER_AGENT = "jech-compat-tests/0.1 (github:vfyjxf)"
# CDN and API downloads from both hosts reject the bare python UA on some paths.
BROWSER_USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)

DEFAULT_TIMEOUT_SEC = 30.0
DEFAULT_RETRIES = 3
DEFAULT_API_MAX_AGE_SEC = 24 * 3600


def with_query(base, params):
    """Build a url with query parameters, encoding lists the way both APIs want."""
    if not params:
        return base
    return base + "?" + urllib.parse.urlencode(params, doseq=True)


class HttpClient:
    def __init__(self, *, refresh=False, timeout=DEFAULT_TIMEOUT_SEC, retries=DEFAULT_RETRIES,
                 api_max_age_sec=DEFAULT_API_MAX_AGE_SEC, cache_root=None, user_agent=API_USER_AGENT):
        self.refresh = bool(refresh)
        self.timeout = timeout
        self.retries = retries
        self.api_max_age_sec = api_max_age_sec
        self.user_agent = user_agent
        self._cache_root = Path(cache_root) if cache_root else paths.http_cache_dir()

    # -- json --------------------------------------------------------------
    def get_json(self, url, *, headers=None, max_age_sec="inherit", what=None, cache=True):
        label = what or url
        cache_path = None
        if cache:
            cache_path = self._json_cache_path(url, headers)
            if not self.refresh:
                cached = self._read_cache(cache_path)
                if cached is not None:
                    age_limit = self.api_max_age_sec if max_age_sec == "inherit" else max_age_sec
                    age = time.time() - cached.get("fetched_at", 0)
                    if age_limit is None or age <= age_limit:
                        return cached.get("body")
        body = self._request(url, headers, what=label)
        try:
            parsed = json.loads(body.decode("utf-8"))
        except (ValueError, UnicodeDecodeError) as exc:
            raise NetworkError(f"{label}: response is not valid json ({exc})")
        if cache_path is not None:
            self._write_cache(cache_path, url, parsed)
        return parsed

    # -- downloads ---------------------------------------------------------
    def download(self, url, dest, *, headers=None, sha1=None, what=None, refresh=False):
        """Fetch ``url`` into ``dest``; reuse the existing file when it still verifies."""
        dest = Path(dest)
        if dest.is_file() and not (refresh or self.refresh):
            if sha1:
                try:
                    if sha1_file(dest).lower() == sha1.lower():
                        return dest
                except OSError:
                    pass
            elif dest.stat().st_size > 0:
                return dest
        label = what or url
        body = self._request(url, headers, what=label, stream=True)
        dest.parent.mkdir(parents=True, exist_ok=True)
        fd, tmp = tempfile.mkstemp(prefix=dest.name + ".", suffix=".part", dir=str(dest.parent))
        try:
            with os.fdopen(fd, "wb") as handle:
                for chunk in iter(lambda: body.read(256 * 1024), b""):
                    handle.write(chunk)
            if sha1:
                actual = sha1_file(tmp)
                if actual.lower() != sha1.lower():
                    raise NetworkError(
                        f"{label}: checksum mismatch (expected sha1 {sha1}, got {actual})"
                    )
            if os.path.getsize(tmp) == 0:
                raise NetworkError(f"{label}: downloaded 0 bytes")
            os.replace(tmp, dest)
            tmp = None
        finally:
            if tmp is not None and os.path.exists(tmp):
                os.unlink(tmp)
        return dest

    # -- internals ---------------------------------------------------------
    def _json_cache_path(self, url, headers):
        # The API key is deliberately part of the key (it changes what an
        # authorised response contains) but the digest is one-way, so the cache
        # never stores or names the secret.
        material = url + "\n" + "\n".join(f"{k}:{v}" for k, v in sorted((headers or {}).items()))
        digest = hashlib.sha256(material.encode("utf-8")).hexdigest()
        self._cache_root.mkdir(parents=True, exist_ok=True)
        return self._cache_root / f"{digest}.json"

    def _read_cache(self, cache_path):
        if not cache_path.is_file():
            return None
        try:
            entry = load_json(cache_path, what=f"http cache {cache_path.name}")
        except Exception:
            return None
        return entry if isinstance(entry, dict) and "body" in entry else None

    def _write_cache(self, cache_path, url, body):
        payload = {"url": url, "fetched_at": time.time(), "body": body}
        tmp = cache_path.with_name(cache_path.name + f".{os.getpid()}.tmp")
        try:
            tmp.write_text(dump_json_text(payload), encoding="utf-8")
            os.replace(tmp, cache_path)
        except OSError:
            if tmp.exists():
                tmp.unlink()
        finally:
            if tmp.exists():
                tmp.unlink()

    def _request(self, url, headers, *, what, stream=False, method="GET", data=None):
        request_headers = {"User-Agent": self.user_agent, "Accept": "application/json"}
        request_headers.update(headers or {})
        last_error = None
        for attempt in range(1, self.retries + 1):
            request = urllib.request.Request(url, data=data, headers=request_headers, method=method)
            try:
                response = urllib.request.urlopen(request, timeout=self.timeout)
                if stream:
                    return response
                with response:
                    return response.read()
            except urllib.error.HTTPError as exc:
                body = ""
                try:
                    body = exc.read(512).decode("utf-8", errors="replace")
                except Exception:
                    pass
                retryable = exc.code in (408, 425, 429, 500, 502, 503, 504)
                if not retryable or attempt == self.retries:
                    raise HttpError(
                        f"{what}: HTTP {exc.code} {exc.reason}",
                        status=exc.code,
                        url=url,
                        body=body.strip(),
                    )
                last_error = exc
            except (urllib.error.URLError, TimeoutError, OSError) as exc:
                last_error = exc
                if attempt == self.retries:
                    raise NetworkError(f"{what}: {exc}")
            if attempt < self.retries:
                time.sleep(min(2 ** attempt, 8))
        raise NetworkError(f"{what}: {last_error}")  # pragma: no cover - loop always returns or raises
