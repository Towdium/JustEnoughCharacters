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

from .. import cache, paths
from ..errors import DependencyError
from ..net import BROWSER_USER_AGENT, HttpClient
from ..util import atomic_write_json, load_json, sha1_file
from . import vanilla

FABRIC_PROFILE_URL = "https://meta.fabricmc.net/v2/versions/loader/{mc}/{loader}/profile/json"
# Which loaders this module can install without a userland installer.
HEADLESS_LOADERS = ("fabric",)


class LoaderInstall:
    def __init__(self, *, loader_type, loader_version, mc, profile_id, main_class,
                 jvm_args=(), game_args=(), libraries=(), notes=()):
        self.loader_type = loader_type
        self.loader_version = loader_version
        self.mc = mc
        # The version id the client reports (and the loader's own name for this
        # install): fabric spells it ``fabric-loader-<loader>-<mc>``.
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


# ---------------------------------------------------------------------------
# fabric
# ---------------------------------------------------------------------------


def _fabric(loader_version, mc, *, http, refresh, log):
    client = http or HttpClient(refresh=refresh)
    url = FABRIC_PROFILE_URL.format(mc=mc, loader=loader_version)
    profile = client.get_json(url, what=f"fabric loader profile {loader_version}/{mc}")
    if not isinstance(profile, dict):
        raise DependencyError(f"{url} did not return a json object")
    main_class = profile.get("mainClass")
    if not main_class:
        raise DependencyError(f"fabric {loader_version}/{mc}: the profile names no mainClass")

    directory = cache.ensure_dir(cache.loader_dir("fabric", loader_version, mc))
    atomic_write_json(cache.loader_profile_path("fabric", loader_version, mc), profile)
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
        atomic_write_json(cache.loader_hashes_path("fabric", loader_version, mc), recorded)
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
