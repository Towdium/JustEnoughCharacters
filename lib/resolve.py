"""``jt resolve``: turn an env's version descriptions into pinned ids.

Resolution is idempotent: a mod whose ``pin`` already carries an id is skipped
(no API call), and the env file is rewritten only when a pin actually changed.
Every artifact is then downloaded into ``<JT_HOME>/cache/mods`` so a later run
needs no network at all — which is also what makes ``jt run`` reproducible.

For the mod under test (``env.jech``), ``source: local-build`` resolves the jar
by glob inside the JECh checkout; the file is used in place and its sha1 is
recorded, so the pack (and the modset hash) changes whenever the build changes.
``env.jech.project`` names the checkout, searched as ``$JT_JECH_ROOT/<project>``
and then ``$JT_JECH_ROOT`` itself, which covers both the workstation layout (a
directory per version line under one root) and a CI branch checkout that already
is the root.
"""

from __future__ import annotations

import os
from pathlib import Path

from . import cache, cfapi, envs, mrapi, modmatch, paths
from .errors import DependencyError, JtError
from .net import BROWSER_USER_AGENT, HttpClient
from .util import fold, jar_mod_ids, sha1_file

JECH_ROOT_ENV = "JT_JECH_ROOT"
NON_RUNTIME_SUFFIXES = ("-sources.jar", "-javadoc.jar")

LOCAL_BUILD_TAG = "local-build"


class ResolvedArtifact:
    def __init__(self, *, role, name, source, filename, path=None, source_path=None,
                 sha1=None, size=0, url=None, pin=None, detail="", via=envs.DEFAULT_VIA):
        self.role = role
        self.name = name
        self.source = source
        self.filename = filename
        self.via = via
        self.path = Path(path) if path else None
        self.source_path = Path(source_path) if source_path else None
        self.sha1 = sha1
        self.size = size
        self.url = url
        self.pin = pin or {}
        self.detail = detail

    @property
    def staged(self):
        """Whether this artifact has to be copied into the game directory's mods/."""
        return self.via != "dev-classpath"

    def describe(self):
        where = str(self.path or self.source_path or "?")
        return f"{self.name} ({self.source}) {self.filename} sha1={(self.sha1 or '?')[:12]} <- {where}"

    def to_json(self):
        return {
            "role": self.role,
            "name": self.name,
            "source": self.source,
            "filename": self.filename,
            "sha1": self.sha1,
            "size": self.size,
            "pin": self.pin,
            "via": self.via,
            "detail": self.detail,
        }


class ResolvedEnv:
    def __init__(self, env, artifacts, *, changed=False, notes=()):
        self.env = env
        self.artifacts = artifacts
        self.changed = changed
        self.notes = list(notes)

    @property
    def jech(self):
        for artifact in self.artifacts:
            if artifact.role == "jech":
                return artifact
        return None

    @property
    def mods(self):
        return [a for a in self.artifacts if a.role == "mod"]

    @property
    def staged(self):
        """The artifacts that have to be copied into the game directory's mods/.

        Everything else rides the checkout's own development classpath (see
        ``envs.MOD_VIA``); keeping the filter here means the pack, the isolation
        step and the report can never disagree about what the game will load.
        """
        return [a for a in self.artifacts if a.staged]

    def __repr__(self):
        return f"ResolvedEnv({self.env.name!r}, {len(self.artifacts)} artifact(s))"




def resolve_env(env, *, refresh=False, download=True, list_only=False, log=print,
                include_jech=True):
    """Resolve (and optionally download) every artifact of one env.

    ``include_jech=False`` leaves the mod under test out: a run whose subject is
    named on the command line (``--subject``) must not also glob the checkout's
    build output, because the jar it tests is exactly the one the command line
    named — and because that glob would fail the run before the subject is even
    looked at when the checkout has not been built yet.
    """
    http = HttpClient(refresh=refresh)
    cf_client = None
    mr_client = None
    notes = []
    changed = False
    artifacts = []

    jech = env.jech if include_jech else None
    if jech is None:
        notes.append("no jar under test is declared here: this run takes it from the command line "
                     "(--subject/--subject-jar), which is the only thing that decides it")
    elif jech.source == LOCAL_BUILD_TAG:
        jar = local_build_jar(env)
        ids = jar_mod_ids(jar)
        if ids and jech.expect_mod_id and jech.expect_mod_id not in ids:
            raise DependencyError(
                f"{env.name}: {paths.display(jar)} declares mod ids {sorted(ids)} but the env expects "
                f"{jech.expect_mod_id!r} — the glob picked up the wrong jar"
            )
        if not ids:
            notes.append(f"jech jar {jar.name} declares no readable mod metadata "
                         "(no META-INF/mods.toml, fabric.mod.json or mcmod.info found)")
        artifact = ResolvedArtifact(
            role="jech", name="jech", source=LOCAL_BUILD_TAG, filename=jar.name,
            source_path=jar, path=jar, sha1=sha1_file(jar), size=jar.stat().st_size,
            detail=f"local build for {jech.expect_mod_id}", via=jech.via,
        )
        artifacts.append(artifact)
    else:
        if list_only:
            _list_candidates(env, jech, log=log)
        pin = jech.pin if jech.pinned else None
        if pin is None:
            if jech.source == "curseforge":
                cf_client = cf_client or cfapi.CurseForgeClient.from_env(http=http)
                client = cf_client
            else:
                mr_client = mr_client or mrapi.ModrinthClient.from_env(http=http)
                client = mr_client
            candidate = client.resolve(jech.slug, env.mc, env.loader.type,
                                      (jech.describe or {}).get("version", ""), what=env.name)
            pin = candidate.pin()
            jech.raw["pin"] = pin
            jech.pin = pin
            changed = True
            log(f"{env.name}: pinned jech -> {pin.get('fileId') or pin.get('versionId')} "
                f"({pin.get('filename')})")
        artifact = _artifact_from_pin(
            role="jech", name="jech", source=jech.source, pin=pin,
            detail=f"{jech.slug} {(jech.describe or {}).get('version', '')}", via=jech.via,
        )
        artifacts.append(artifact)

    for mod in env.mods:
        if list_only:
            _list_candidates(env, mod, log=log)
        pin = mod.pin if mod.pinned else None
        if pin is None:
            if mod.source == "curseforge":
                cf_client = cf_client or cfapi.CurseForgeClient.from_env(http=http)
                client = cf_client
            else:
                mr_client = mr_client or mrapi.ModrinthClient.from_env(http=http)
                client = mr_client
            candidate = client.resolve(mod.slug, env.mc, env.loader.type,
                                      (mod.describe or {}).get("version", ""), what=f"{env.name}/{mod.name}")
            pin = candidate.pin()
            mod.raw["pin"] = pin
            mod.pin = pin
            changed = True
            log(f"{env.name}: pinned {mod.name} -> {pin.get('fileId') or pin.get('versionId')} "
                f"({pin.get('filename')})")
        artifacts.append(_artifact_from_pin(role="mod", name=mod.name, source=mod.source, pin=pin,
                                            detail=f"{mod.slug} {(mod.describe or {}).get('version', '')}",
                                            via=mod.via))

    if download:
        for artifact in artifacts:
            ensure_download(artifact, http=http, refresh=refresh, log=log)

    if changed:
        env.write()
        log(f"{env.name}: env file updated with pinned ids ({paths.display(env.path)})")

    return ResolvedEnv(env, artifacts, changed=changed, notes=notes)


def resolve_envs(selected, *, refresh=False, download=True, list_only=False, log=print):
    return [resolve_env(env, refresh=refresh, download=download, list_only=list_only, log=log)
            for env in selected]




def search_roots(env, env_root):
    """The two ways a checkout root can be laid out, given ``env.jech.project``.

    ``project`` names a directory *under* the root (the workstation layout: one
    root holding several version-line checkouts) **and** may be redundant (a CI
    checkout of one branch, where the branch root already is what ``project``
    names and ``compat-tests`` rides along as a submodule). Both are tried, the
    project-scoped one first; the caller only complains when neither matched.
    """
    project = env.jech.project
    if not project:
        return []
    return [Path(env_root) / project, Path(env_root)]


def jech_base_root():
    """The root the version-line checkouts live under.

    ``$JT_JECH_ROOT`` relocates the whole search (the selftest uses it, and CI
    points it at the branch checkout root); the default is the checkout directory
    that holds ``compat-tests``, which is right both on this workstation (its
    sibling directories are the version lines) and for a submodule CI layout.
    """
    env_root = os.environ.get(JECH_ROOT_ENV)
    return Path(os.path.expanduser(env_root)) if env_root else paths.jech_root()


def local_build_jar(env):
    """The jar the env's ``jech.path`` glob selects, from the JECh checkouts.

    With ``jech.project`` the glob is tried root by root in the order
    :func:`search_roots` gives and the first root holding a match wins, so a
    layout that answers at both levels (a branch checkout root that also holds a
    ``<project>`` directory) resolves the same way as the plain workstation
    layout, instead of looking ambiguous. Without a project there is no
    authority to choose a checkout, so every subdirectory is scanned and two
    matches are an ambiguity error rather than a silent pick.
    """
    pattern = env.jech.path
    if not pattern:
        raise DependencyError(f"{env.name}: jech.path is empty")
    if Path(pattern).is_absolute():
        raise DependencyError(
            f"{env.name}: jech.path must be relative to a JECh checkout, got an absolute path"
        )
    base_root = jech_base_root()
    ordered_roots = search_roots(env, base_root)
    if ordered_roots:
        searched = list(ordered_roots)
        roots = ordered_roots
        first_match_wins = True
    else:
        roots = (sorted(p for p in base_root.iterdir() if p.is_dir())
                 if base_root.is_dir() else [])
        searched = list(roots)
        first_match_wins = False
    files = []
    for base in roots:
        group = jars_under(base, pattern)
        if not group:
            continue
        if first_match_wins:
            files = group
            break
        files.extend(group)
    if not files:
        where = ", ".join(paths.display(p) for p in searched) or "(no checkout found)"
        raise DependencyError(
            f"{env.name}: no jar matches jech.path {pattern!r} in {where}. "
            "Build the mod for this version line first, or set env.jech.project to name the "
            f"checkout (also settable via ${JECH_ROOT_ENV})."
        )
    if len(files) > 1:
        listing = "\n".join(f"  {paths.display(p)}" for p in sorted(files))
        raise DependencyError(
            f"{env.name}: jech.path {pattern!r} is ambiguous, {len(files)} jars match:\n{listing}\n"
            "Narrow the glob (or add env.jech.project) so exactly one jar is selected."
        )
    return files[0]


def jars_under(base, pattern):
    """The runtime jars one root contributes: build output, never sources/javadoc."""
    group = [c for c in sorted(Path(base).glob(pattern)) if c.is_file() and c.suffix == ".jar"]
    runtime = [c for c in group if not fold(c.name).endswith(NON_RUNTIME_SUFFIXES)]
    return runtime or group




def cache_path_for(filename):
    return paths.mods_cache_dir() / filename


def _artifact_from_pin(*, role, name, source, pin, detail, via=envs.DEFAULT_VIA):
    filename = pin.get("filename")
    if not filename:
        raise DependencyError(
            f"{name}: pin {pin.get('fileId') or pin.get('versionId')} has no filename; "
            "run jt resolve with --refresh after deleting the pin to re-resolve it"
        )
    return ResolvedArtifact(
        role=role, name=name, source=source, filename=filename,
        path=cache_path_for(filename), sha1=pin.get("sha1"), size=pin.get("size") or 0,
        url=pin.get("downloadUrl"), pin=pin, detail=detail, via=via,
    )


def download_urls(artifact):
    """Candidate urls, best first: the pinned one, then the source's own layout."""
    pin = artifact.pin or {}
    urls = []
    for candidate in (artifact.url, pin.get("downloadUrl")):
        if candidate and candidate not in urls:
            urls.append(candidate)
    if artifact.source == "curseforge" and pin.get("fileId") and artifact.filename:
        for host in (cfapi.CDN_HOST, cfapi.CDN_HOST_ALT):
            cdn = cfapi.cdn_url(pin["fileId"], artifact.filename, host=host)
            if cdn not in urls:
                urls.append(cdn)
    if artifact.source == "modrinth" and pin.get("versionId"):
        version_url = f"{mrapi.BASE_URL}/version/{pin['versionId']}"
        if version_url not in urls:
            urls.append(version_url)
    return urls


def ensure_download(artifact, *, http, refresh=False, log=print):
    """Fetch the artifact into the cache when it is not already there and verified.

    A downloaded artifact is a cache layer like any other, so both outcomes are
    said out loud (``cache: mod jei hit: ...``): without that line the only way to
    tell where a run's mod jars came from was to read the game directory's symlink
    targets. An artifact that is the checkout's own build output gets no line —
    nothing about it is cached.
    """
    if artifact.source == LOCAL_BUILD_TAG or artifact.path is None:
        if artifact.path is not None and artifact.path.is_file():
            if not artifact.sha1:
                artifact.sha1 = sha1_file(artifact.path)
            artifact.size = artifact.path.stat().st_size
        return artifact
    dest = artifact.path
    if dest.is_file() and not refresh:
        if not artifact.sha1:
            artifact.sha1 = sha1_file(dest)
        artifact.size = dest.stat().st_size
        cache.note(f"{artifact.role} {artifact.name}", dest, log=log, cached=True)
        return artifact
    urls = download_urls(artifact)
    if not urls:
        raise DependencyError(f"{artifact.name}: no download url for pinned file {artifact.filename!r}")
    errors = []
    for index, url in enumerate(urls):
        try:
            if url.startswith(mrapi.BASE_URL + "/version/"):
                version = http.get_json(url, what=f"modrinth version {artifact.pin.get('versionId')}")
                files = version.get("files") or []
                primary = [f for f in files if f.get("primary")] or files
                if not primary:
                    raise DependencyError(f"{artifact.name}: modrinth version has no files")
                file_entry = primary[0]
                url = file_entry.get("url")
                artifact.sha1 = artifact.sha1 or (file_entry.get("hashes") or {}).get("sha1")
            http.download(url, dest, headers={"User-Agent": BROWSER_USER_AGENT}, sha1=artifact.sha1,
                          what=f"{artifact.name} ({artifact.filename})", refresh=refresh)
            log(f"{artifact.name}: cached {artifact.filename} ({dest.stat().st_size} bytes)")
            cache.note(f"{artifact.role} {artifact.name}", dest, log=log, cached=False)
            artifact.size = dest.stat().st_size
            if not artifact.sha1:
                artifact.sha1 = sha1_file(dest)
            return artifact
        except JtError as exc:
            errors.append(f"  {url}: {exc}")
            if index == len(urls) - 1:
                raise DependencyError(
                    f"{artifact.name}: could not download {artifact.filename}:\n" + "\n".join(errors)
                )
    return artifact




def _list_candidates(env, spec, *, log):
    """``--list``: show what a description would resolve to, without pinning."""
    http = HttpClient()
    loader = env.loader.type
    try:
        if spec.source == "curseforge":
            client = cfapi.CurseForgeClient.from_env(http=http)
            mod = client.search_mod(spec.slug)
            candidates = client.list_files(mod.get("id"), env.mc, loader)
        else:
            client = mrapi.ModrinthClient(http=http)
            candidates = client.versions(spec.slug, env.mc, loader)
    except JtError as exc:
        log(f"{env.name}: cannot list candidates for {spec.slug}: {exc}")
        return
    from . import modmatch
    label = getattr(spec, "name", None) or "jech"
    wanted = (spec.describe or {}).get("version", "")
    log(f"{env.name}: {label} source={spec.source} slug={spec.slug} mc={env.mc} loader={loader} "
        f"describe={wanted!r}")
    for candidate in sorted(candidates, key=lambda c: c.newest_key, reverse=True)[:modmatch.LIST_LIMIT]:
        log("  " + candidate.describe())
    log("  " + f"({len(candidates)} candidate(s))")
