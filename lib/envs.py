"""Loading and validating ``envs/*.json``.

The schema is documented for humans in ``envs/README.md``; this module is its
executable form. Validation is strict and total: every problem in a file is
collected and reported together, unknown keys are rejected (a typo in a key is a
silent no-op otherwise), and ``resolve`` writes pinned ids back into the very
dict that was parsed here so the file keeps its own key order and comments.
"""

from __future__ import annotations

import re
from pathlib import Path

from . import paths
from .errors import EnvSchemaError
from .util import atomic_write_text, dump_json_text, load_json

LOADER_TYPES = ("forge", "neoforge", "fabric", "quilt")
MOD_SOURCES = ("curseforge", "modrinth")
JECH_SOURCES = ("local-build", "curseforge", "modrinth")
CHECK_TYPES = ("log_contains", "log_not_contains", "log_regex", "no_crash_reports", "exit_code_zero",
               "file_exists")
CHECK_VALUE_REQUIRED = ("log_contains", "log_not_contains", "log_regex", "file_exists")

# Checks whose value is a path inside the instance's minecraft directory, and
# therefore must stay relative: an absolute value would silently escape it.
CHECK_PATH_TYPES = ("file_exists",)

# The id key a resolved pin must carry, per source.
_PIN_ID_KEYS = {"curseforge": "fileId", "modrinth": "versionId"}

# mrpack dependency keys, used by pack.py; kept next to the schema so the two
# never drift apart.
MRPACK_DEPENDENCY_KEYS = {
    "forge": "forge",
    "neoforge": "neoforge",
    "fabric": "fabric-loader",
    "quilt": "quilt-loader",
}

DEFAULT_TIMEOUT_SEC = 600
DEFAULT_MAX_MEM_MB = 3072
DEFAULT_MIN_MEM_MB = 1024
DEFAULT_WINDOW = (1280, 720)


class LoaderSpec:
    def __init__(self, type_, version):
        self.type = type_
        self.version = version

    def __repr__(self):
        return f"LoaderSpec({self.type!r}, {self.version!r})"


class JvmSpec:
    def __init__(self, max_mem_mb, min_mem_mb, args, window):
        self.max_mem_mb = max_mem_mb
        self.min_mem_mb = min_mem_mb
        self.args = args
        self.window = window

    def jvm_args(self):
        """Only the *extra* JVM arguments.

        Heap size is carried by Prism's own ``MinMemAlloc``/``MaxMemAlloc`` keys,
        never by ``-Xmx``/``-Xms`` in ``JvmArgs``: Prism treats those two in
        ``JvmArgs`` as a manual override and pops a modal warning dialog on every
        launch, which no headless run can dismiss. So ``jvm_args`` returns just
        the env's ``args`` (usually empty) and memory travels in its own keys.
        """
        return " ".join(self.args)


class ModSpec:
    def __init__(self, name, source, slug, describe, pin, raw):
        self.name = name
        self.source = source
        self.slug = slug
        self.describe = describe
        self.pin = pin
        self.raw = raw

    @property
    def pinned(self):
        return isinstance(self.pin, dict) and bool(self.pin)


class ReadyWhen:
    """The optional log line that marks the game as *ready* for this env.

    A production instance has no in-game runner, so nothing makes the game quit
    on its own. Declaring ``ready_when`` gives the runner a stopping rule: once
    that line (together with every ``log_contains`` marker) has appeared in
    ``latest.log``, the runner ends the instance's process tree itself and
    judges the run, instead of waiting for a natural exit or the watchdog.
    """

    def __init__(self, value):
        self.value = value

    def __repr__(self):
        return f"ReadyWhen({self.value!r})"


class WorldSpec:
    """The optional staged world an env is launched into.

    A world turns a run from "the client reached the main menu" into "the client
    is inside a level", which is the only state some registration work happens in
    (JEI 19.x runs ``registerAdvancedSearch`` — the phase that logs JECh's
    ``Registering JEI pinyin search storage`` — when a level is loaded). The seed
    itself is produced by ``jt seed-world`` with a vanilla dedicated server and
    cached under ``<JT_HOME>/cache/worlds``; the env only names it.
    """

    def __init__(self, name):
        self.name = name

    @property
    def saves_path(self):
        """Where the world sits inside the instance's ``minecraft`` directory."""
        return f"saves/{self.name}"

    def __repr__(self):
        return f"WorldSpec({self.name!r})"


class JechSpec:
    def __init__(self, source, path, expect_mod_id, slug, describe, pin, project, raw):
        self.source = source
        self.path = path
        self.expect_mod_id = expect_mod_id
        self.slug = slug
        self.describe = describe
        self.pin = pin
        self.project = project
        self.raw = raw

    @property
    def pinned(self):
        return isinstance(self.pin, dict) and bool(self.pin)


class CheckSpec:
    def __init__(self, type_, name, value, raw):
        self.type = type_
        self.name = name or default_check_name(type_, value)
        self.value = value
        self.raw = raw


def default_check_name(type_, value):
    if isinstance(value, (list, tuple)):
        value = ",".join(str(v) for v in value)
    if value is None:
        return type_
    text = str(value).replace("\n", " ")
    if len(text) > 40:
        text = text[:37] + "..."
    return f"{type_}:{text}"


class Env:
    """One parsed, validated env file."""

    def __init__(self, *, name, tags, mc, loader, java_version, jech, mods, checks,
                 timeout_sec, jvm, path, raw, ready_when=None, skip=None, world=None):
        self.name = name
        # An env that declares ``skip`` also carries the ``skip`` tag, so a
        # selection can say "everything runnable" as ``--exclude tag:skip``
        # instead of naming the skipped envs one by one. The tag is derived, not
        # written: ``write()`` persists ``raw``, which never gains it.
        self.tags = list(tags) + (["skip"] if skip and "skip" not in tags else [])
        self.mc = mc
        self.loader = loader
        self.java_version = java_version
        self.jech = jech
        self.mods = mods
        self.checks = checks
        self.timeout_sec = timeout_sec
        self.jvm = jvm
        self.ready_when = ready_when
        self.skip = skip
        self.world = world
        self.path = Path(path)
        self.raw = raw

    def __repr__(self):
        return f"Env({self.name!r}, mc={self.mc!r}, {self.loader.type})"

    @property
    def mod_label(self):
        return f"{self.mc}/{self.loader.type}"

    def write(self):
        """Persist the (possibly pin-updated) raw dict back to the env file."""
        atomic_write_text(self.path, dump_json_text(self.raw))
        return self.path


# ---------------------------------------------------------------------------
# loading
# ---------------------------------------------------------------------------


def env_files(directory=None):
    directory = Path(directory) if directory else paths.envs_dir()
    if not directory.is_dir():
        return []
    return sorted(p for p in directory.glob("*.json"))


def load_env(path):
    data = load_json(path, what=f"env {Path(path).name}")
    return parse_env(data, path)


def parse_env(data, path="<dict>"):
    problems = []
    if not isinstance(data, dict):
        raise EnvSchemaError(f"{path}: env must be a json object, got {type(data).__name__}")

    _reject_unknown(data, {"name", "tags", "mc", "loader", "java", "jech", "mods", "checks",
                           "timeout_sec", "jvm", "ready_when", "skip", "world"}, "env", problems)

    name = _string(data, "name", "env", problems, required=True)
    if name is not None and not _NAME_RE.match(name):
        problems.append(f"env.name {name!r} must match [A-Za-z0-9._-]+ (it becomes a run id)")
    tags = _string_list(data, "tags", "env", problems, required=False)
    mc = _string(data, "mc", "env", problems, required=True)
    if mc is not None and not _VERSION_RE.match(mc):
        problems.append(f"env.mc {mc!r} does not look like a Minecraft version (e.g. 1.21.1)")

    loader = _parse_loader(data.get("loader"), problems)
    java_version = _parse_java(data.get("java"), problems)
    jech = _parse_jech(data.get("jech"), problems, mc, loader)
    mods = _parse_mods(data.get("mods"), problems)
    checks = _parse_checks(data.get("checks"), problems)
    timeout_sec = _parse_timeout(data.get("timeout_sec"), problems)
    jvm = _parse_jvm(data.get("jvm"), problems)
    ready_when = _parse_ready_when(data.get("ready_when"), problems)
    skip = _parse_skip(data.get("skip"), problems)
    world = _parse_world(data.get("world"), problems)

    if problems:
        raise EnvSchemaError(
            f"{path}: {len(problems)} schema problem(s):\n  - " + "\n  - ".join(problems)
        )

    return Env(name=name, tags=tags, mc=mc, loader=loader, java_version=java_version, jech=jech,
               mods=mods, checks=checks, timeout_sec=timeout_sec, jvm=jvm, path=path, raw=data,
               ready_when=ready_when, skip=skip, world=world)


def load_all(directory=None):
    envs = []
    for path in env_files(directory):
        envs.append(load_env(path))
    return envs


def find_env(selector, directory=None):
    """Resolve a single env by name or by file stem; used by ``jt resolve``."""
    envs = load_all(directory)
    matches = [e for e in envs if e.name == selector or e.path.stem == selector]
    if not matches:
        known = ", ".join(sorted(e.name for e in envs)) or "(none)"
        raise EnvSchemaError(f"no env named {selector!r}; known envs: {known}")
    if len(matches) > 1:
        raise EnvSchemaError(f"env selector {selector!r} is ambiguous: "
                             + ", ".join(sorted(str(e.path) for e in matches)))
    return matches[0]


# ---------------------------------------------------------------------------
# field parsers
# ---------------------------------------------------------------------------

_NAME_RE = re.compile(r"^[A-Za-z0-9._-]+$")
_VERSION_RE = re.compile(r"^\d+(\.\d+)+$")
# A world name becomes one directory under saves/ and one zip entry inside the
# pack, so it may not carry a separator or a parent-directory hop.
_WORLD_NAME_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9 ._-]*$")


def _reject_unknown(data, allowed, context, problems):
    for key in sorted(data):
        if key in allowed:
            continue
        if key.startswith("_") or key == "$schema":
            continue  # documented comment slot
        problems.append(
            f"{context}: unknown key {key!r} (allowed: {', '.join(sorted(allowed))}; "
            "prefix a comment with '_' instead)"
        )


def _string(data, key, context, problems, *, required):
    if key not in data or data[key] is None:
        if required:
            problems.append(f"{context}: missing required key {key!r}")
        return None
    value = data[key]
    if not isinstance(value, str) or not value.strip():
        problems.append(f"{context}.{key} must be a non-empty string")
        return None
    return value.strip()


def _string_list(data, key, context, problems, *, required):
    if key not in data or data[key] is None:
        if required:
            problems.append(f"{context}: missing required key {key!r}")
        return []
    value = data[key]
    if not isinstance(value, list) or any(not isinstance(v, str) or not v.strip() for v in value):
        problems.append(f"{context}.{key} must be a list of non-empty strings")
        return []
    return [v.strip() for v in value]


def _int(data, key, context, problems, *, required, minimum=None):
    if key not in data or data[key] is None:
        if required:
            problems.append(f"{context}: missing required key {key!r}")
        return None
    value = data[key]
    if isinstance(value, bool) or not isinstance(value, int):
        problems.append(f"{context}.{key} must be an integer")
        return None
    if minimum is not None and value < minimum:
        problems.append(f"{context}.{key} must be >= {minimum}, got {value}")
        return None
    return value


def _parse_loader(data, problems):
    if not isinstance(data, dict):
        problems.append("env.loader must be an object {type, version}")
        return None
    _reject_unknown(data, {"type", "version"}, "env.loader", problems)
    type_ = _string(data, "type", "env.loader", problems, required=True)
    version = _string(data, "version", "env.loader", problems, required=True)
    if type_ is not None and type_ not in LOADER_TYPES:
        problems.append(f"env.loader.type {type_!r} is not one of {', '.join(LOADER_TYPES)}")
        type_ = None
    return LoaderSpec(type_, version) if type_ and version else None


def _parse_java(data, problems):
    if not isinstance(data, dict):
        problems.append("env.java must be an object {version}")
        return None
    _reject_unknown(data, {"version"}, "env.java", problems)
    version = _int(data, "version", "env.java", problems, required=True, minimum=8)
    return version


def _parse_jvm(data, problems):
    data = data if data is not None else {}
    if not isinstance(data, dict):
        problems.append("env.jvm must be an object {max_mem_mb, min_mem_mb, args, window}")
        return JvmSpec(DEFAULT_MAX_MEM_MB, DEFAULT_MIN_MEM_MB, [], DEFAULT_WINDOW)
    _reject_unknown(data, {"max_mem_mb", "min_mem_mb", "args", "window"}, "env.jvm", problems)
    max_mem = _int(data, "max_mem_mb", "env.jvm", problems, required=False, minimum=512)
    min_mem = _int(data, "min_mem_mb", "env.jvm", problems, required=False, minimum=256)
    args = _string_list(data, "args", "env.jvm", problems, required=False)
    window = DEFAULT_WINDOW
    if "window" in data and data["window"] is not None:
        raw = data["window"]
        if (not isinstance(raw, list) or len(raw) != 2
                or any(isinstance(v, bool) or not isinstance(v, int) or v < 320 for v in raw)):
            problems.append("env.jvm.window must be [width, height] with integers >= 320")
        else:
            window = (raw[0], raw[1])
    return JvmSpec(max_mem or DEFAULT_MAX_MEM_MB, min_mem or min(min_mem or DEFAULT_MIN_MEM_MB, max_mem or DEFAULT_MAX_MEM_MB), args, window)


def _parse_timeout(data, problems):
    if data is None:
        return DEFAULT_TIMEOUT_SEC
    if isinstance(data, bool) or not isinstance(data, int) or data < 30:
        problems.append("env.timeout_sec must be an integer >= 30")
        return DEFAULT_TIMEOUT_SEC
    return data


def _parse_ready_when(data, problems):
    """``ready_when`` is optional; when present it must be ``{"value": "<line>"}``."""
    if data is None:
        return None
    if not isinstance(data, dict):
        problems.append('env.ready_when must be an object {"value": "<log line>"}')
        return None
    _reject_unknown(data, {"value"}, "env.ready_when", problems)
    value = _string(data, "value", "env.ready_when", problems, required=True)
    return ReadyWhen(value) if value else None


def _parse_skip(data, problems):
    """``skip`` is optional; when present it is the reason the env is not executed.

    A skipped env is never run and never rendered as a pass — the reason is
    required precisely so the report can say *why* nothing was verified.
    """
    if data is None:
        return None
    if not isinstance(data, str) or not data.strip():
        problems.append("env.skip must be a non-empty string (the reason the env is not run)")
        return None
    return data.strip()


def _parse_world(data, problems):
    """``world`` is optional; when present it must be ``{"name": "<saves folder>"}``."""
    if data is None:
        return None
    if not isinstance(data, dict):
        problems.append('env.world must be an object {"name": "<world folder>"}')
        return None
    _reject_unknown(data, {"name"}, "env.world", problems)
    name = _string(data, "name", "env.world", problems, required=True)
    if name is None:
        return None
    if not _WORLD_NAME_RE.match(name) or name in (".", ".."):
        problems.append(
            f"env.world.name {name!r} must be a plain folder name (no path separators, "
            "not '.' or '..'): it becomes saves/<name> inside the instance"
        )
        return None
    return WorldSpec(name)


def _parse_describe(data, context, problems):
    if not isinstance(data, dict):
        problems.append(f"{context}.describe must be an object {{\"version\": \"...\"}}")
        return None
    _reject_unknown(data, {"version"}, f"{context}.describe", problems)
    version = _string(data, "version", f"{context}.describe", problems, required=True)
    return {"version": version} if version else None


def _parse_pin(data, source, context, problems):
    if data is None:
        return None
    if not isinstance(data, dict):
        problems.append(f"{context}.pin must be null or an object")
        return None
    if source not in _PIN_ID_KEYS:
        problems.append(f"{context}.pin is not supported for source {source!r}")
        return None
    required = _PIN_ID_KEYS[source]
    allowed = {"fileId", "versionId", "filename", "downloadUrl", "sha1", "sha512", "size"}
    _reject_unknown(data, allowed, f"{context}.pin", problems)
    if required not in data:
        problems.append(f"{context}.pin for source {source!r} must carry {required!r}")
        return None
    value = data[required]
    if isinstance(value, bool) or not isinstance(value, (int, str)) or not str(value).strip():
        problems.append(f"{context}.pin.{required} must be a non-empty id")
        return None
    filename = data.get("filename")
    if filename is not None and (not isinstance(filename, str) or not filename.strip()):
        problems.append(f"{context}.pin.filename must be a non-empty string")
        return None
    return data


def _parse_jech(data, problems, mc, loader):
    if not isinstance(data, dict):
        problems.append("env.jech must be an object {source, ...}")
        return None
    _reject_unknown(data, {"source", "path", "expectModId", "slug", "describe", "pin", "project"},
                    "env.jech", problems)
    source = _string(data, "source", "env.jech", problems, required=True)
    if source is not None and source not in JECH_SOURCES:
        problems.append(f"env.jech.source {source!r} is not one of {', '.join(JECH_SOURCES)}")
        source = None
    path = expect = slug = describe = None
    pin = None
    project = data.get("project")
    if project is not None and (not isinstance(project, str) or not project.strip()):
        problems.append("env.jech.project must be a non-empty string (a directory under the JECh root)")
        project = None
    if source == "local-build":
        path = _string(data, "path", "env.jech", problems, required=True)
        if path is not None and Path(path).is_absolute():
            problems.append("env.jech.path must be relative (the harness has no absolute paths)")
        expect = _string(data, "expectModId", "env.jech", problems, required=True)
    elif source in ("curseforge", "modrinth"):
        slug = _string(data, "slug", "env.jech", problems, required=True)
        describe = _parse_describe(data.get("describe"), "env.jech", problems)
        if "describe" not in data:
            problems.append("env.jech.describe is required when source is "
                            f"{source!r} (the JECh version to pin)")
        pin = _parse_pin(data.get("pin"), source, "env.jech", problems)
    return JechSpec(source, path, expect, slug, describe, pin, project, data)


def _parse_mods(data, problems):
    if not isinstance(data, list):
        problems.append("env.mods must be a list (possibly empty)")
        return []
    mods = []
    seen = set()
    for index, item in enumerate(data):
        context = f"env.mods[{index}]"
        if not isinstance(item, dict):
            problems.append(f"{context} must be an object")
            continue
        _reject_unknown(item, {"name", "source", "slug", "describe", "pin", "tags"},
                        context, problems)
        name = _string(item, "name", context, problems, required=True)
        if name and name in seen:
            problems.append(f"{context}.name {name!r} is a duplicate")
        seen.add(name)
        source = _string(item, "source", context, problems, required=True)
        if source is not None and source not in MOD_SOURCES:
            problems.append(f"{context}.source {source!r} is not one of {', '.join(MOD_SOURCES)}")
            source = None
        slug = _string(item, "slug", context, problems, required=True)
        describe = _parse_describe(item.get("describe"), context, problems)
        if "describe" not in item:
            problems.append(f"{context}.describe is required (the version to pin)")
        pin = _parse_pin(item.get("pin"), source, context, problems) if source else None
        tags = _string_list(item, "tags", context, problems, required=False)
        if name and source and slug and describe:
            mods.append(ModSpec(name, source, slug, describe, pin, item))
    return mods


def _parse_checks(data, problems):
    if not isinstance(data, list):
        problems.append("env.checks must be a list (possibly empty)")
        return []
    checks = []
    names = set()
    for index, item in enumerate(data):
        context = f"env.checks[{index}]"
        if not isinstance(item, dict):
            problems.append(f"{context} must be an object")
            continue
        _reject_unknown(item, {"type", "value", "name", "tags"}, context, problems)
        type_ = _string(item, "type", context, problems, required=True)
        if type_ is not None and type_ not in CHECK_TYPES:
            problems.append(f"{context}.type {type_!r} is not one of {', '.join(CHECK_TYPES)}")
            type_ = None
        value = item.get("value")
        if type_ in CHECK_VALUE_REQUIRED:
            ok = isinstance(value, str) and bool(value.strip())
            if not ok and isinstance(value, str):
                ok = bool(value)  # an explicitly empty pattern is a config mistake
            if not isinstance(value, (str, list)):
                problems.append(f"{context}.value is required for {type_} (string, or list of strings)")
                value = None
            elif isinstance(value, list):
                if not value or any(not isinstance(v, str) or not v.strip() for v in value):
                    problems.append(f"{context}.value must be a non-empty list of non-empty strings")
                    value = None
            elif not value.strip() and type_ != "log_regex":
                problems.append(f"{context}.value must not be blank for {type_}")
                value = None
            if type_ in CHECK_PATH_TYPES and value is not None:
                absolute = [v for v in (value if isinstance(value, list) else [value])
                            if Path(v).is_absolute()]
                if absolute:
                    problems.append(
                        f"{context}.value must be relative to the instance's minecraft directory, "
                        f"got an absolute path: {absolute[0]!r}"
                    )
                    value = None
        elif value is not None:
            problems.append(f"{context}.value is not allowed for {type_}")
            value = None
        name = item.get("name")
        if name is not None and (not isinstance(name, str) or not name.strip()):
            problems.append(f"{context}.name must be a non-empty string")
            name = None
        resolved_name = (name or default_check_name(type_ or "check", value)).strip()
        if resolved_name in names:
            problems.append(f"{context}.name {resolved_name!r} is a duplicate")
        names.add(resolved_name)
        if type_:
            checks.append(CheckSpec(type_, resolved_name, value, item))
    return checks
