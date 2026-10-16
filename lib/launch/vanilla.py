"""One Minecraft version's own files, straight from Mojang's metadata.

Two json documents describe a version, both fetched through ``lib.net``'s cache:

1. ``version_manifest_v2.json`` → the entry for this version id → that version's
   own json (``lib.mojang`` already walks those two hops for the server jar);
2. the version json itself: ``downloads.client``, ``libraries[]``,
   ``assetIndex``, ``logging.client``, ``mainClass``, ``arguments`` and the
   ``javaVersion`` the client needs.

Everything lands under ``cache/mc/<mc>`` (see ``lib.cache``) and every download is
sha1-verified, so a second run of the same version re-uses the tree instead of
re-fetching it.

Two details of that metadata are easy to get wrong and are handled here on
purpose:

* **Rules.** A library or an argument is included only when its ``rules`` allow
  it for this operating system and architecture. This is where macOS gets
  ``-XstartOnFirstThread`` (its rule is ``os.name: osx``) and where the arm64
  natives of a modern version come from.
* **Natives, two eras.** Up to roughly 1.18 the version json names a native
  classifier per library (``natives: {osx: natives-macos}`` +
  ``downloads.classifiers``) and the *launcher* extracts those jars into
  ``natives_directory``. From 1.19 on there is no ``natives`` key at all: the
  natives are ordinary libraries with an os rule (``org.lwjgl:lwjgl:3.3.3:
  natives-macos-arm64``), they belong on the **classpath**, and LWJGL extracts
  the ones for the running machine itself into the directory that
  ``-Dorg.lwjgl.system.SharedLibraryExtractPath`` names. Both eras are
  implemented; which one a version uses is measured from its own metadata
  rather than assumed from its version number.
"""

from __future__ import annotations

import concurrent.futures
import platform as platform_mod
import re
import sys
import zipfile
from pathlib import Path

from .. import cache, mojang, paths
from ..errors import DependencyError
from ..net import BROWSER_USER_AGENT, HttpClient
from ..util import atomic_write_text, load_json, sha1_file

ASSET_OBJECT_URL = "https://resources.download.minecraft.net"
ASSET_WORKERS = 8
PLACEHOLDER = re.compile(r"\$\{([^}]+)\}")
DEFAULT_EXCLUDE = ("META-INF/",)




def platform_name():
    if sys.platform == "darwin":
        return "osx"
    if sys.platform.startswith("win"):
        return "windows"
    return "linux"


def platform_arch():
    machine = (platform_mod.machine() or "").lower()
    if machine in ("arm64", "aarch64"):
        return "arm64"
    if machine in ("x86_64", "amd64"):
        return "x86_64"
    return "x86"


def platform_tag():
    """``<os>-<arch>`` — the key a version's extracted natives are filed under."""
    return f"{platform_name()}-{platform_arch()}"


def os_version():
    """A best-effort OS release string for ``os.version`` rules (may be empty)."""
    name = platform_name()
    try:
        if name == "osx":
            return platform_mod.mac_ver()[0] or ""
        if name == "windows":
            return platform_mod.win32_ver()[0] or ""
        return platform_mod.release() or ""
    except Exception:  # pragma: no cover - platform probes are best-effort
        return ""



LWJGL_ARM64_VERSION = "3.3.1"
LWJGL_LIBRARY_BASE_URL = "https://libraries.minecraft.net"
LWJGL_MACOS_ARM64 = (
    ("lwjgl", "ae58664f88e18a9bb2c77b063833ca7aaec484cb", 724243,
     "71d0d5e469c9c95351eb949064497e3391616ac9", 42693),
    ("lwjgl-glfw", "cbac1b8d30cb4795149c1ef540f912671a8616d0", 128801,
     "cac0d3f712a3da7641fa174735a5f315de7ffe0a", 129077),
    ("lwjgl-jemalloc", "a817bcf213db49f710603677457567c37d53e103", 36601,
     "e577b87d8ad2ade361aaea2fcf226c660b15dee8", 103475),
    ("lwjgl-openal", "2623a6b8ae1dfcd880738656a9f0243d2e6840bd", 88237,
     "23d55e7490b57495320f6c9e1936d78fd72c4ef8", 346125),
    ("lwjgl-opengl", "831a5533a21a5f4f81bbc51bb13e9899319b5411", 921563,
     "eafe34b871d966292e8db0f1f3d6b8b110d4e91d", 41665),
    ("lwjgl-stb", "b119297cf8ed01f247abe8685857f8e7fcf5980f", 112380,
     "fcf073ed911752abdca5f0b00a53cfdf17ff8e8b", 178408),
    ("lwjgl-tinyfd", "0ff1914111ef2e3e0110ef2dabc8d8cdaad82347", 6767,
     "972ecc17bad3571e81162153077b4d47b7b9eaa9", 41380),
)


def lwjgl_for_this_machine(mc, entries, *, log=print):
    """A version's own library entries, with the LWJGL this machine can load.

    Two gates, both about the version rather than about a row, so a future row on an
    old version inherits the fix and a version that needs nothing is untouched:

    * only on **macOS arm64** (``osx-arm64``) — an x86_64 or Linux machine loads the
      version's own natives happily, and replaces nothing;
    * only when the version's own LWJGL publishes **no arm64 natives**. A version
      that already carries them (1.19 on, whichever shape its metadata uses) is left
      exactly as Mojang wrote it, rules and classifiers included.

    When both hold, the version's ``org.lwjgl:*`` entries are dropped and the LWJGL
    3.3.1 module set above takes their place — the same switch the row's Gradle
    backend makes, and the one its baseline dev client is measured on. Every other
    library, natives classifiers among them, is passed through untouched.

    The substitution is logged: a run whose classpath silently carries a different
    LWJGL than the version's metadata names is otherwise rediscovered as a native
    load failure with no relation to the JSON a reader is looking at.
    """
    if not needs_arm64_native_support(entries):
        return entries
    own = [entry for entry in entries if _is_lwjgl_entry(entry)]
    replaced = sorted({_lwjgl_version(entry) for entry in own})
    kept = [entry for entry in entries if not _is_lwjgl_entry(entry)]
    log(f"cache: mc {mc} lwjgl: {', '.join(replaced)} -> {LWJGL_ARM64_VERSION} for osx-arm64 "
        f"({len(LWJGL_MACOS_ARM64)} module(s) plus their natives-macos-arm64 jars); the version's "
        "own jars are x86_64-only, so this machine cannot load them")
    return kept + _lwjgl_arm64_entries()


def _is_lwjgl_entry(entry):
    """Whether one library entry is one of the version's own LWJGL 3 modules."""
    parts = str(entry.get("name") or "").split(":")
    return len(parts) >= 3 and parts[0] == "org.lwjgl"


def _lwjgl_version(entry):
    return str(entry.get("name") or "").split(":")[2]


def _lwjgl_has_arm64_natives(entry):
    """Whether an entry already carries the natives an arm64 client needs.

    Both eras of the metadata are read: the classic one names the classifier in its
    ``natives`` map, the modern one spells it in the entry's own name.
    """
    natives = entry.get("natives")
    if isinstance(natives, dict) and any("arm64" in str(name) for name in natives.values()):
        return True
    return "natives-macos-arm64" in str(entry.get("name") or "")


def _lwjgl_arm64_entries():
    """The LWJGL module set above, in the metadata shape the launcher already reads."""
    entries = []
    for artifact, jar_sha1, jar_size, native_sha1, native_size in LWJGL_MACOS_ARM64:
        plain = f"org.lwjgl:{artifact}:{LWJGL_ARM64_VERSION}"
        entries.append(_library_entry(plain, jar_sha1, jar_size))
        entries.append(_library_entry(
            f"{plain}:natives-macos-arm64", native_sha1, native_size,
            rules=[{"action": "allow", "os": {"name": "osx"}}],
        ))
    return entries


def _library_entry(name, sha1, size, *, rules=None):
    """One synthesised library entry, with the url its own metadata would publish."""
    path = maven_path(name)
    entry = {
        "name": name,
        "downloads": {"artifact": {
            "path": path,
            "url": f"{LWJGL_LIBRARY_BASE_URL}/{path}",
            "sha1": sha1,
            "size": size,
        }},
    }
    if rules:
        entry["rules"] = rules
    return entry



NATIVE_SUPPORT_FILENAME = "fabric-loom-native-support-1.0.1.jar"
NATIVE_SUPPORT_URL = (
    "https://maven.fabricmc.net/net/fabricmc/fabric-loom-native-support/1.0.1/"
    + NATIVE_SUPPORT_FILENAME
)
NATIVE_SUPPORT_SHA1 = "fa285e15e860a868f97137322261cf6cdb3d5854"


def needs_arm64_native_support(entries):
    """Whether this version's own metadata is x86_64-only as far as macOS goes.

    True for a version whose LWJGL publishes no arm64 natives: the same versions
    :func:`lwjgl_for_this_machine` has to replace LWJGL for, and the versions whose
    window shim the runner adds (see the block above). Everywhere else — another
    platform, or a version that carries arm64 natives — both are off, so a modern
    version's run is untouched.
    """
    if platform_tag() != "osx-arm64":
        return False
    own = [entry for entry in entries if _is_lwjgl_entry(entry)]
    return bool(own) and not any(_lwjgl_has_arm64_natives(entry) for entry in own)


def ensure_native_support(*, http=None, refresh=False, log=print):
    """Fetch (or reuse) that shim; returns the jar the run's ``mods/`` wants.

    Cached like every other jar this launcher downloads: under
    ``<JT_HOME>/cache/mods``, sha1-verified, and reused when the bytes are already
    the artifact.
    """
    client = http or HttpClient(refresh=refresh, log=log)
    dest = paths.mods_cache_dir() / NATIVE_SUPPORT_FILENAME
    existed = dest.is_file()
    client.download(NATIVE_SUPPORT_URL, dest, headers={"User-Agent": BROWSER_USER_AGENT},
                    sha1=NATIVE_SUPPORT_SHA1, what="macOS window shim", refresh=refresh)
    cache.note("shim " + NATIVE_SUPPORT_FILENAME[:-len(".jar")], dest, log=log, cached=existed)
    return dest




def rule_matches(rule, features=None):
    """Whether one entry of a ``rules`` list applies to this machine.

    ``os.name``/``os.arch`` are matched exactly. An ``os.version`` regex is
    matched against :func:`os_version` when this platform can report one; when it
    cannot, the version clause is treated as satisfied, because the alternative
    (dropping the entry) fails the launch outright, while keeping it is at worst
    an extra library on the classpath.
    """
    os_block = rule.get("os") or {}
    if os_block.get("name") and os_block["name"] != platform_name():
        return False
    if os_block.get("arch") and os_block["arch"] != platform_arch():
        return False
    pattern = os_block.get("version")
    if pattern:
        version = os_version()
        if version and not re.search(pattern, version):
            return False
    wanted_features = rule.get("features") or {}
    for name, wanted in wanted_features.items():
        if bool((features or {}).get(name)) != bool(wanted):
            return False
    return True


def allowed(rules, features=None):
    """Mojang's rule fold: no rules means allow, else the last match decides."""
    if not rules:
        return True
    result = False
    for rule in rules:
        if rule_matches(rule, features):
            result = rule.get("action") == "allow"
    return result


def substitute(text, values):
    """Replace ``${name}`` placeholders, refusing to invent a missing value.

    Every argument that survives the rule filter is one this launcher is expected
    to be able to fill in; an unknown placeholder means the launcher would pass a
    literal ``${...}`` to the client, so it is an error naming the placeholder.
    """
    def replace(match):
        key = match.group(1)
        if key not in values:
            raise DependencyError(
                f"the launch metadata asks for ${{{key}}}, which this launcher does not provide; "
                "a run would have passed the placeholder through to the client unchanged"
            )
        return str(values[key])

    return PLACEHOLDER.sub(replace, text)


def expand(entries, *, features=None, values=None):
    """A version json argument list (strings and rule-carrying objects) flattened.

    A string entry is taken as-is; an object contributes its ``value`` (a string
    or a list of them) only when its rules allow it for this machine.
    """
    out = []
    for entry in entries or []:
        if isinstance(entry, str):
            out.append(entry)
            continue
        if not isinstance(entry, dict):
            continue
        if not allowed(entry.get("rules"), features):
            continue
        value = entry.get("value")
        if isinstance(value, str):
            out.append(value)
        elif isinstance(value, list):
            out.extend(str(item) for item in value)
    if values is not None:
        out = [substitute(item, values) for item in out]
    return out




class Library:
    def __init__(self, name, path, *, url=None, sha1=None, size=0, native=False):
        self.name = name
        self.path = Path(path)
        self.url = url
        self.sha1 = sha1
        self.size = size
        self.native = native

    def __repr__(self):
        return f"Library({self.name!r}, {self.path.name}, native={self.native})"


class ClientInstall:
    """Everything one Minecraft version contributes to a launch."""

    def __init__(self, *, mc, version_json, client_jar, libraries, natives_dir, natives,
                 asset_index_id, assets_dir, main_class, jvm_args, game_args,
                 logging_argument=None, java_major=None, notes=()):
        self.mc = mc
        self.version_json = version_json
        self.client_jar = Path(client_jar)
        self.libraries = list(libraries)
        self.natives_dir = Path(natives_dir)
        self.natives = list(natives)
        self.asset_index_id = asset_index_id
        self.assets_dir = Path(assets_dir)
        self.main_class = main_class
        self.jvm_args = list(jvm_args)
        self.game_args = list(game_args)
        self.logging_argument = logging_argument  # e.g. -Dlog4j.configurationFile=<file>
        self.java_major = java_major
        self.notes = list(notes)

    @property
    def classpath(self):
        """The version's own classpath: the client jar, then its libraries."""
        return [self.client_jar] + [library.path for library in self.libraries]

    def describe(self):
        return (f"{self.mc}: client.jar {self.client_jar.stat().st_size} bytes, "
                f"{len(self.libraries)} librar(y|ies), {len(self.natives)} natives jar(s), "
                f"assets index {self.asset_index_id}, java {self.java_major or '?'}")


def ensure_client(mc, *, http=None, refresh=False, assets=True, log=print):
    """Fetch (or reuse) everything a version's client needs; returns the install."""
    client = http or HttpClient(refresh=refresh, log=log)
    notes = []
    version_json = mojang.version_json(mc, client, refresh=refresh)

    client_download = (version_json.get("downloads") or {}).get("client")
    if not isinstance(client_download, dict) or not client_download.get("url"):
        raise DependencyError(
            f"{mc}: this version's json publishes no client download, so no client can be launched"
        )
    jar = cache.client_jar(mc)
    existed = jar.is_file()
    client.download(client_download["url"], jar,
                    headers={"User-Agent": BROWSER_USER_AGENT}, sha1=client_download.get("sha1"),
                    what=f"Minecraft {mc} client", refresh=refresh)
    cache.note(f"mc {mc} client.jar", jar, log=log, cached=existed)

    libraries, natives, natives_dir = _libraries(
        mc, lwjgl_for_this_machine(mc, version_json.get("libraries") or [], log=log),
        client, refresh=refresh, log=log,
    )
    asset_index_id, assets_dir = _assets(mc, version_json, client, refresh=refresh,
                                        download=assets, log=log)
    logging_argument = _logging(mc, version_json, client, refresh=refresh, log=log)

    arguments = version_json.get("arguments")
    if not isinstance(arguments, dict):
        raise DependencyError(
            f"{mc}: this version's json has no ``arguments`` block (it predates 1.13's argument "
            "list). The direct launcher does not implement the legacy ``minecraftArguments`` "
            "form yet — that arrives with the 1.12.2 line."
        )
    java = version_json.get("javaVersion") or {}
    main_class = version_json.get("mainClass")
    if not main_class:
        raise DependencyError(f"{mc}: the version json names no mainClass")
    if not asset_index_id:
        notes.append(f"{mc}: the version json declares no assetIndex; the client starts without one")
    return ClientInstall(
        mc=mc, version_json=version_json, client_jar=jar, libraries=libraries,
        natives_dir=natives_dir, natives=natives, asset_index_id=asset_index_id,
        assets_dir=assets_dir, main_class=main_class,
        jvm_args=arguments.get("jvm") or [], game_args=arguments.get("game") or [],
        logging_argument=logging_argument, java_major=java.get("majorVersion"), notes=notes,
    )




def _libraries(mc, entries, http, *, refresh, log):
    libraries = []
    natives = []
    natives_root = cache.natives_dir(mc, platform_tag())
    natives_dir = cache.ensure_dir(natives_root)
    counts = {"hit": 0, "fetch": 0}
    native_hashes = []
    for entry in entries:
        if not allowed(entry.get("rules")):
            continue
        lib = _library(mc, entry, http, refresh=refresh, log=log, counts=counts)
        downloads = entry.get("downloads") or {}
        classifier = _native_classifier(entry)
        if classifier is not None:
            native = _native_library(mc, entry, downloads, classifier, http, refresh=refresh, log=log)
            if native is not None:
                natives.append(native)
                native_hashes.append(native.sha1 or native.path.name)
                _extract_native(native, natives_dir, entry.get("extract"), log=log)
        if lib is not None:
            libraries.append(lib)
    if native_hashes:
        _stamp_natives(natives_dir, native_hashes, log=log)
    log(f"cache: mc {mc} libraries: {counts['hit']} reused, {counts['fetch']} fetched "
        f"({len(libraries)} on the classpath, {len(natives)} natives jar(s) extracted to "
        f"{paths.display(natives_dir)})")
    return libraries, natives, natives_dir


def maven_path(name):
    """``group:artifact:version[:classifier]`` → its maven repository path."""
    parts = str(name or "").split(":")
    if len(parts) < 3:
        raise DependencyError(f"library name {name!r} is not a maven coordinate")
    group, artifact, version = parts[0], parts[1], parts[2]
    classifier = parts[3] if len(parts) > 3 else None
    stem = f"{artifact}-{version}" + (f"-{classifier}" if classifier else "")
    return "/".join([group.replace(".", "/"), artifact, version, stem + ".jar"])


def library_key(name):
    """The identity that decides whether two declarations are the same library.

    ``<group>:<artifact>:<classifier>``. The version is left out on purpose and the
    classifier is kept on purpose; :func:`merge_libraries` carries both reasons. A
    name that is not a maven coordinate keys to itself, so it can never fold into
    anything else.
    """
    parts = str(name or "").split(":")
    if len(parts) < 3:
        return str(name or "")
    classifier = parts[3] if len(parts) > 3 else ""
    return f"{parts[0]}:{parts[1]}:{classifier}"


def merge_libraries(base, overlay, *, log=print):
    """``base`` (the version's own libraries) with ``overlay`` (the loader's) over it.

    This is the launcher's ``inheritsFrom`` rule, and it is not decoration: a
    loader profile *inherits* the version it installs on, so its libraries are
    merged into the version's rather than concatenated after them. Two identity
    rules make the merge what it is, and both are easy to "simplify" into a crash:

    * **the version is not part of the identity.** A re-declaration with a newer
      version replaces the inherited one — that is the whole point of the rule, and
      it is what has to happen here: 1.21.10's own metadata ships
      ``org.ow2.asm:asm:9.6``, the fabric 0.19.2 profile wants ``9.9``, and
      concatenating the two lists puts two ``org/objectweb/asm/ClassReader.class``
      on the classpath, which fabric's loader refuses by design
      (``LoaderUtil.verifyClasspath``: "duplicate ASM classes found on classpath")
      — a hard startup failure, not a shadowed jar;
    * **the classifier is part of the identity.** ``org.lwjgl:lwjgl:3.3.3`` and
      ``org.lwjgl:lwjgl:3.3.3:natives-macos-arm64`` are two different jars, and in
      a version of the modern shape natives *are* ordinary libraries on the
      classpath: a key without the classifier would fold the natives into their
      plain sibling and silently drop the macOS natives, which is a launch that
      fails much later and much less legibly than the duplicate above. The classic
      shape is no different: a natives-classified entry is still a natives jar to
      extract, never a plain classpath entry, and it never reaches this merge at
      all (it is not one of the version's classpath libraries).

    On a collision ``overlay`` wins (it is the inheriting side) and the *base*
    entry is dropped, so the classpath is exactly the concatenation minus the
    shadowed version's jar — the surviving entry keeps the place the overlay gives
    it, and every library the overlay does not re-declare is untouched, natives
    flags included.

    Each replacement is logged: it is a silent override of one jar by another, and
    a reader of a run's log should not have to infer it from a crash.
    """
    claimed = {library_key(library.name): library for library in overlay}
    merged = []
    replaced = []
    for library in base:
        winner = claimed.get(library_key(library.name))
        if winner is None:
            merged.append(library)
        else:
            replaced.append((library, winner))
    merged.extend(overlay)
    for loser, winner in replaced:
        log(f"cache: library {library_key(winner.name)} replaced: {loser.name} -> {winner.name} "
            "(an inheriting declaration wins; only its jar goes on the classpath)")
    return merged


def _library(mc, entry, http, *, refresh, log, counts):
    """One library's main artifact, downloaded when the metadata names one."""
    artifact = (entry.get("downloads") or {}).get("artifact")
    name = entry.get("name")
    if not isinstance(artifact, dict) or not artifact.get("url"):
        log(f"cache: mc {mc} library {name} publishes no main artifact; it contributes nothing to "
            "the classpath")
        return None
    dest = cache.libraries_dir(mc) / artifact["path"]
    existed = dest.is_file()
    http.download(artifact["url"], dest, headers={"User-Agent": BROWSER_USER_AGENT},
                  sha1=artifact.get("sha1"), what=f"library {name}", refresh=refresh)
    counts["hit" if existed else "fetch"] += 1
    return Library(name, dest, url=artifact["url"], sha1=artifact.get("sha1"),
                   size=artifact.get("size") or 0)


def _native_classifier(entry):
    """The natives classifier this machine needs from one library entry, if any."""
    natives = entry.get("natives")
    if not isinstance(natives, dict):
        return None
    classifier = natives.get(platform_name())
    if not classifier:
        return None
    return str(classifier).replace("${arch}", "64" if platform_arch() != "x86" else "32")


def _native_library(mc, entry, downloads, classifier, http, *, refresh, log):
    artifact = (downloads.get("classifiers") or {}).get(classifier)
    if not isinstance(artifact, dict) or not artifact.get("url"):
        raise DependencyError(
            f"{mc}: library {entry.get('name')} declares native classifier {classifier!r}, but the "
            "version json publishes no download for it"
        )
    dest = cache.native_jars_dir(mc) / artifact.get("path", Path(maven_path(entry.get("name"))).name)
    existed = dest.is_file()
    http.download(artifact["url"], dest, headers={"User-Agent": BROWSER_USER_AGENT},
                  sha1=artifact.get("sha1"), what=f"natives {entry.get('name')}", refresh=refresh)
    cache.note(f"mc {mc} natives {classifier}", dest, log=log, cached=existed)
    return Library(entry.get("name"), dest, url=artifact["url"], sha1=artifact.get("sha1"),
                   size=artifact.get("size") or 0, native=True)


def _extract_native(library, dest, extract, *, log):
    if not library.path.is_file():
        return
    excludes = list((extract or {}).get("exclude") or DEFAULT_EXCLUDE)
    dest.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(library.path) as archive:
        for member in archive.namelist():
            if member.endswith("/"):
                continue
            if any(member.startswith(prefix) for prefix in excludes):
                continue
            archive.extract(member, dest)
    log(f"cache: extracted {library.path.name} into {paths.display(dest)}")


def _stamp_natives(natives_dir, hashes, *, log):
    """Record which natives jars are extracted, so a run can tell it is complete."""
    stamp = natives_dir / ".extracted"
    try:
        current = stamp.read_text(encoding="utf-8")
    except OSError:
        current = ""
    if current == "\n".join(hashes) + "\n":
        return
    atomic_write_text(stamp, "\n".join(hashes) + "\n")




def _assets(mc, version_json, http, *, refresh, download, log):
    index = version_json.get("assetIndex")
    assets_root = cache.assets_dir(mc)
    if not isinstance(index, dict) or not index.get("url"):
        return None, assets_root
    index_id = index.get("id") or version_json.get("assets")
    index_path = cache.ensure_dir(assets_root / "indexes") / f"{index_id}.json"
    existed = index_path.is_file()
    http.download(index["url"], index_path, headers={"User-Agent": BROWSER_USER_AGENT},
                  sha1=index.get("sha1"), what=f"asset index {index_id}", refresh=refresh)
    cache.note(f"mc {mc} asset index", index_path, log=log, cached=existed)
    if download:
        _asset_objects(mc, index_path, assets_root, http, refresh=refresh, log=log)
    return index_id, assets_root


def _object_ok(path, digest, size):
    """Whether a cached asset object is the object the index names."""
    try:
        if size and path.stat().st_size != size:
            return False
        return sha1_file(path) == digest
    except OSError:
        return False


def _asset_objects(mc, index_path, assets_root, http, *, refresh, log):
    """Fetch every object the index names, eight at a time.

    The objects are the bulk of a Minecraft download (~800 MB for 1.21.1), so they
    are fetched concurrently and counted: the log line says how much of the work
    this run actually had to do, which is what makes "the cache is warm" a
    statement someone can check rather than believe.
    """
    index = load_json(index_path, what=f"asset index {index_path.name}")
    objects = index.get("objects") if isinstance(index, dict) else None
    if not isinstance(objects, dict):
        raise DependencyError(f"{index_path}: the asset index carries no objects map")
    wanted = []
    for name, entry in objects.items():
        digest = (entry or {}).get("hash")
        if not digest:
            continue
        dest = assets_root / "objects" / digest[:2] / digest
        if dest.is_file() and _object_ok(dest, digest, entry.get("size")):
            continue
        wanted.append((digest, dest))
    if not wanted:
        log(f"cache: mc {mc} assets: all {len(objects)} object(s) already cached")
        return
    log(f"cache: mc {mc} assets: fetching {len(wanted)} of {len(objects)} object(s)")
    failures = []

    def fetch(item):
        digest, dest = item
        url = f"{ASSET_OBJECT_URL}/{digest[:2]}/{digest}"
        try:
            http.download(url, dest, headers={"User-Agent": BROWSER_USER_AGENT}, sha1=digest,
                          what=f"asset {digest[:12]}", refresh=refresh)
        except Exception as exc:  # noqa: BLE001 - reported as a batch below
            failures.append(f"{digest[:12]}: {exc}")

    with concurrent.futures.ThreadPoolExecutor(max_workers=ASSET_WORKERS) as pool:
        list(pool.map(fetch, wanted))
    if failures:
        raise DependencyError(
            f"{mc}: {len(failures)} asset object(s) could not be fetched:\n  "
            + "\n  ".join(failures[:10])
        )
    log(f"cache: mc {mc} assets: {len(wanted)} object(s) fetched")




def _logging(mc, version_json, http, *, refresh, log):
    """The log4j configuration the client is started with, when it publishes one.

    It is not optional decoration: every check in this harness greps the game log,
    and the shape of a log line comes from this file.
    """
    logging_block = version_json.get("logging")
    client_block = (logging_block or {}).get("client") if isinstance(logging_block, dict) else None
    if not isinstance(client_block, dict):
        return None
    file_entry = client_block.get("file") or {}
    url = file_entry.get("url")
    if not url:
        return None
    dest = cache.logging_dir(mc) / (file_entry.get("id") or "client-log4j.xml")
    existed = dest.is_file()
    http.download(url, dest, headers={"User-Agent": BROWSER_USER_AGENT}, sha1=file_entry.get("sha1"),
                  what=f"logging config {file_entry.get('id')}", refresh=refresh)
    cache.note(f"mc {mc} logging config", dest, log=log, cached=existed)
    return substitute(client_block.get("argument") or "", {"path": dest}) or None
