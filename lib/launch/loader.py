"""The mod loader's install on top of a vanilla version.

A loader contributes three things to a launch: a ``mainClass`` that wraps the
game's own, its own libraries, and whatever JVM arguments it wants. For Fabric
all three come from one headless json document, which is why Fabric is the loader
this wave implements:

``https://meta.fabricmc.net/v2/versions/loader/<mc>/<loader>/profile/json``

That profile is complete — no installer runs, no userdev artifact, no patch
tree. Its libraries are maven coordinates with a repository url; two of them
(``intermediary`` and the loader itself) publish no hash, and those are verified
against the maven ``.sha1`` sidecar and, failing that, against what the first
download recorded (``cache/loader/<type>-<version>-<mc>/sha1.json``), so a
corrupt download cannot be mistaken for a complete one.

The Forge family (installer or ``install_profile.json``) and 1.12.2's
launchwrapper are deliberately *not* guessed at here: an env whose loader is not
yet implemented fails with a message that says so, rather than launching
something that is not the environment the row describes.
"""

from __future__ import annotations

import os
from pathlib import Path

from .. import cache, paths
from ..errors import ConfigError, DependencyError
from ..net import BROWSER_USER_AGENT, HttpClient
from ..util import atomic_write_text, dump_json_text, load_json, sha1_file
from . import vanilla

FABRIC_PROFILE_URL = "https://meta.fabricmc.net/v2/versions/loader/{mc}/{loader}/profile/json"
HEADLESS_LOADERS = ("fabric",)
LOADER_STATE_DIRNAME = ".fabric"


class LoaderInstall:
    def __init__(self, *, loader_type, loader_version, mc, profile_id, main_class,
                 jvm_args=(), game_args=(), libraries=(), notes=()):
        self.loader_type = loader_type
        self.loader_version = loader_version
        self.mc = mc
        self.profile_id = profile_id
        self.main_class = main_class
        self.jvm_args = list(jvm_args)
        self.game_args = list(game_args)
        self.libraries = list(libraries)
        self.notes = list(notes)

    @property
    def classpath(self):
        return [library.path for library in self.libraries]

    def describe(self):
        return (f"{self.loader_type} {self.loader_version} on {self.mc}: mainClass "
                f"{self.main_class}, {len(self.libraries)} librar(y|ies), profile {self.profile_id}")


def ensure_loader(loader_type, loader_version, mc, *, http=None, refresh=False, log=print):
    """Install (or reuse) one loader for one Minecraft version."""
    if loader_type == "fabric":
        return _fabric(loader_version, mc, http=http, refresh=refresh, log=log)
    raise DependencyError(
        f"{loader_type}: the direct launcher implements {', '.join(HEADLESS_LOADERS)} first; "
        f"{loader_type} needs its own install step (an installer or an install_profile.json) and "
        "arrives with its own wave. Run this row through the checkout backend until then, or "
        "implement it in lib/launch/loader.py"
    )


def share_remap_state(install, client, *, game_dir, log=print):
    """Make ``<game dir>/.fabric`` a link to the loader's shared remap state.

    Loader does this work on "first launch" — its own words for the case where the
    output it wants is not there yet — and writes it into the *game directory*,
    which the direct launcher gives every run a fresh one of. Nothing about the
    result depends on the run: it is derived from the client jar, the loader and
    the jars in ``mods/``, so it belongs in the cache, keyed by the first two (a
    mod's entry is content-addressed by loader itself, so a changed mod set adds
    entries to the layered state instead of needing a layer of its own). The link
    makes loader's "is my output already there?" test answer yes on every run
    after the first.

    The link is a **symlink to the state directory**, not hardlinks to the files
    inside it. Two reasons, both about how loader writes:

    * it both adds files (a mod's ``processedMods`` entry the last run did not
      have) and rewrites them; a hardlink is only correct for a file whose content
      never changes, and the one case that matters — loader replacing a remapped
      jar by rename — would leave the *cache* holding the stale file while the
      run used the new one, which is exactly the failure the sha1 in the layer's
      name exists to prevent. Through a symlink, both an in-place write and a
      rename-over land in the cache, whichever loader chooses;
    * a directory cannot be hardlinked at all, and the state directory is new on
      the first run: there are no files to link yet.

    A real (non-symlink) ``.fabric`` is refused rather than deleted: the run
    directory is assembled fresh for every run, so one can only be there if
    something else is already using that game directory.
    """
    sha1 = client_sha1(client)
    directory = cache.loader_remap_dir(install.loader_type, install.loader_version, install.mc, sha1)
    populated = directory.is_dir() and any(directory.iterdir())
    cache.ensure_dir(directory)
    link = Path(game_dir) / LOADER_STATE_DIRNAME
    if link.is_symlink():
        if os.readlink(link) != str(directory):
            link.unlink()
            os.symlink(directory, link)
    elif link.exists():
        raise ConfigError(
            f"{paths.display(link)} already exists and is not a link to the shared loader state "
            f"({paths.display(directory)}); refusing to replace a game directory something else "
            "has been using"
        )
    else:
        os.symlink(directory, link)
    cache.note(f"loader {install.loader_type} {install.loader_version} remap state", directory,
               log=log, cached=populated)
    return directory


def client_sha1(client):
    """The sha1 of the client jar one install remaps: from the metadata, or hashed.

    The version json already carries it, and it is the value the download was
    verified against, so it is preferred over re-reading 26 MiB off disk.
    """
    published = ((client.version_json.get("downloads") or {}).get("client") or {}).get("sha1")
    if isinstance(published, str) and len(published) == 40:
        return published.lower()
    return sha1_file(client.client_jar)




def _fabric(loader_version, mc, *, http, refresh, log):
    client = http or HttpClient(refresh=refresh, log=log)
    url = FABRIC_PROFILE_URL.format(mc=mc, loader=loader_version)
    profile = client.get_json(url, what=f"fabric loader profile {loader_version}/{mc}")
    if not isinstance(profile, dict):
        raise DependencyError(f"{url} did not return a json object")
    main_class = profile.get("mainClass")
    if not main_class:
        raise DependencyError(f"fabric {loader_version}/{mc}: the profile names no mainClass")

    directory = cache.ensure_dir(cache.loader_dir("fabric", loader_version, mc))
    write_json_if_changed(cache.loader_profile_path("fabric", loader_version, mc), profile)
    libraries_dir = cache.loader_libraries_dir("fabric", loader_version, mc)
    recorded = _recorded_hashes("fabric", loader_version, mc)
    libraries = []
    counts = {"hit": 0, "fetch": 0}
    for entry in profile.get("libraries") or []:
        name = entry.get("name")
        path = vanilla.maven_path(name)
        repository = entry.get("url") or "https://maven.fabricmc.net/"
        url = repository.rstrip("/") + "/" + path
        dest = libraries_dir / path
        sha1 = entry.get("sha1") or recorded.get(path) or _sidecar_sha1(client, url, log=log)
        existed = dest.is_file()
        client.download(url, dest, headers={"User-Agent": BROWSER_USER_AGENT}, sha1=sha1,
                        what=f"library {name}", refresh=refresh)
        counts["hit" if existed else "fetch"] += 1
        if not sha1:
            sha1 = sha1_file(dest)
            recorded[path] = sha1
        libraries.append(vanilla.Library(name, dest, url=url, sha1=sha1,
                                         size=entry.get("size") or 0))
    if recorded:
        write_json_if_changed(cache.loader_hashes_path("fabric", loader_version, mc), recorded)
    arguments = profile.get("arguments") or {}
    install = LoaderInstall(
        loader_type="fabric", loader_version=loader_version, mc=mc,
        profile_id=profile.get("id") or f"fabric-loader-{loader_version}-{mc}",
        main_class=main_class, jvm_args=arguments.get("jvm") or [],
        game_args=arguments.get("game") or [], libraries=libraries,
    )
    log(f"cache: loader fabric {loader_version} on {mc}: {counts['hit']} librar(y|ies) reused, "
        f"{counts['fetch']} fetched ({len(libraries)} total) in {paths.display(directory)}")
    return install


def _recorded_hashes(loader_type, loader_version, mc):
    path = cache.loader_hashes_path(loader_type, loader_version, mc)
    try:
        recorded = load_json(path, what=f"loader hash record {path.name}")
    except Exception:
        return {}
    return recorded if isinstance(recorded, dict) else {}


def write_json_if_changed(path, payload):
    """Write json only when the file's bytes would actually change.

    Loader metadata is written on every run and is the same bytes every time;
    ``os.replace`` moves the mtime anyway, which makes "the cache is not written
    during a run" false for a file whose content never changed. Returns whether it
    wrote, so a caller could say so — the loader layers here have nothing to add
    to their own ``cache:`` line.
    """
    text = dump_json_text(payload)
    try:
        if Path(path).read_text(encoding="utf-8") == text:
            return False
    except OSError:
        pass
    atomic_write_text(path, text)
    return True


def _sidecar_sha1(http, url, *, log):
    """The maven ``.sha1`` sidecar for a file whose own metadata publishes none."""
    try:
        text = http.get_text(url + ".sha1", what=f"sha1 sidecar for {url.rsplit('/', 1)[-1]}")
    except Exception as exc:  # noqa: BLE001 - a missing sidecar is not fatal
        log(f"cache: no sha1 sidecar for {url.rsplit('/', 1)[-1]} ({exc}); the download will be "
            "hashed and recorded instead")
        return None
    token = text.split()[0] if text.split() else ""
    return token.lower() if len(token) == 40 else None
