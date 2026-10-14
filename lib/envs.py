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
# Where an artifact's jar ends up. ``game-dir`` copies it into the game
# directory's ``mods/`` (what a non-dev launch needs); ``dev-classpath``
# stages nothing because the checkout's own Gradle run already carries it —
# either as the mod under test (the source set the run config registers) or as a
# declared dependency (``localRuntime`` / ``modImplementation``), and a second
# copy of the same mod id in ``mods/`` is a double load, not a second opinion.
MOD_VIA = ("game-dir", "dev-classpath")
DEFAULT_VIA = "game-dir"
CHECK_TYPES = ("log_contains", "log_not_contains", "log_regex", "no_crash_reports", "exit_code_zero",
               "file_exists", "uitest_report")
CHECK_VALUE_REQUIRED = ("log_contains", "log_not_contains", "log_regex", "file_exists")

# Checks whose value is a path inside the run's game directory, and
# therefore must stay relative: an absolute value would silently escape it.
# ``uitest_report``'s is optional and its default lives in checks.py; the rest
# require one.
CHECK_PATH_TYPES = ("file_exists", "uitest_report")
CHECK_PATH_OPTIONAL_TYPES = ("uitest_report",)

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


class LoaderSpec:
    def __init__(self, type_, version):
        self.type = type_
        self.version = version

    def __repr__(self):
        return f"LoaderSpec({self.type!r}, {self.version!r})"


class ModSpec:
    def __init__(self, name, source, slug, describe, pin, raw, via=DEFAULT_VIA):
        self.name = name
        self.source = source
        self.slug = slug
        self.describe = describe
        self.pin = pin
        # ``game-dir`` (default) stages the jar into the game directory's mods/;
        # ``dev-classpath`` records it but stages nothing, because the checkout's
        # Gradle run already provides it.
        self.via = via
        self.raw = raw

    @property
    def pinned(self):
        return isinstance(self.pin, dict) and bool(self.pin)


class GradleSpec:
    """The ``env.gradle`` block: launch through a Gradle run task.

    An env that declares this block is executed by running a Gradle task inside
    the JECh checkout under test (``./gradlew <task>``). The checkout's own run
    configuration owns the client's JDK (its toolchain) and its runtime
    classpath; the harness owns the game directory, the isolation around it and
    the judging. The block is **required** for every env that is not declared
    ``skip``: it is the only launch layer (see :func:`parse_env`).

    * ``task`` — the Gradle task to run, spelled from the build root
      (``:neoforge:runClient``), never a bare ``runClient``: the build root is the
      version-line checkout and the modules are its subprojects.
    * ``project_dir`` — the directory to run Gradle in, relative to
      ``$JT_JECH_ROOT``; defaults to the env's ``jech.project``, so the two
      always name the same checkout unless an env says otherwise.
    * ``daemon_java`` — the major version of the JDK that *runs Gradle* (its
      JAVA_HOME), for a wrapper whose Gradle cannot run on the machine default;
      ``None`` keeps the ambient JAVA_HOME. It is not the client's JDK: that
      comes from the checkout's toolchain.
    * ``args`` — extra ``-P``/``-D`` arguments handed to the Gradle command line
      verbatim, for a checkout property the harness owns but the runner cannot
      spell by name. The only user today is the ``1.12.0`` checkout's
      ``-PdevMod=jei|hei``, which picks the JEI-line mod the checkout stages: a
      per-row choice the env file is the only place to name. Kept a list of plain
      strings, so nothing here interprets them.
    """

    def __init__(self, task, project_dir, daemon_java=None, args=()):
        self.task = task
        self.project_dir = project_dir
        self.daemon_java = daemon_java
        self.args = tuple(args)

    def __repr__(self):
        return (f"GradleSpec({self.task!r}, project_dir={self.project_dir!r}, "
                f"daemon_java={self.daemon_java!r}, args={list(self.args)!r})")


class UiTestSpec:
    """The optional ``env.uitest`` block: run the checkout's in-client UI test harness.

    An env that declares this block runs the checkout's UI test harness inside
    the client: the selection is handed to Gradle as ``-PjechTest=<selection>``,
    which is the only gate on the harness wiring, so the run loads the harness
    mod, drives the scenarios the selection names, and writes its own report,
    captures and ``junit.xml`` into ``<game dir>/uitest`` before it quits the
    client itself. The env's ``gradle.task`` therefore has to be the harness's
    own run task — the block only says *which scenarios*, never *how to launch*.

    * ``selection`` — the harness's scenario selection expression, in the
      grammar its own ``ScenarioSelection`` accepts: ``all``, a scenario name,
      ``a,b`` (terms are OR-ed), ``tag:<tag>``, ``group:<group>``, ``mod:<mod>``,
      ``regex:<pattern>``. It is passed through verbatim; an expression that
      matches nothing is a failed run, not an empty pass.

    The evidence is read back by the ``uitest_report`` check, so an env with this
    block is judged by the harness's own verdict rather than by the log alone.
    """

    def __init__(self, selection):
        self.selection = selection

    def __repr__(self):
        return f"UiTestSpec({self.selection!r})"


class ReadyWhen:
    """The optional log line that marks the game as *ready* for this env.

    A client launched by a checkout's run task has no in-game runner, so nothing makes it quit
    on its own. Declaring ``ready_when`` gives the runner a stopping rule: once
    that line (together with every ``log_contains`` marker) has appeared in
    ``latest.log``, the runner ends the game's process tree itself and
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
        """Where the world sits inside the run's game directory."""
        return f"saves/{self.name}"

    def __repr__(self):
        return f"WorldSpec({self.name!r})"


class JechSpec:
    def __init__(self, source, path, expect_mod_id, slug, describe, pin, project, raw,
                 via=DEFAULT_VIA):
        self.source = source
        self.path = path
        self.expect_mod_id = expect_mod_id
        self.slug = slug
        self.describe = describe
        self.pin = pin
        self.project = project
        # The mod under test is staged like any other artifact; see ``MOD_VIA``.
        self.via = via
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
                 timeout_sec, path, raw, ready_when=None, skip=None, world=None,
                 gradle=None, uitest=None):
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
        self.ready_when = ready_when
        self.skip = skip
        self.world = world
        # Every env that is not declared ``skip`` must name a Gradle run task in
        # the checkout under test: that is the only launch layer there is (see
        # :class:`GradleSpec`).
        self.gradle = gradle
        # ``None`` means "judge this run by the log alone"; a :class:`UiTestSpec`
        # makes the checkout's in-client UI test harness run too, and its report
        # the evidence (see lib/checks.py's ``uitest_report``).
        self.uitest = uitest
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
                           "timeout_sec", "ready_when", "skip", "world", "gradle", "uitest"},
                    "env", problems)

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
    ready_when = _parse_ready_when(data.get("ready_when"), problems)
    skip = _parse_skip(data.get("skip"), problems)
    world = _parse_world(data.get("world"), problems)
    # ``project_dir`` defaults to the checkout ``jech.project`` already names, so
    # a gradle env cannot accidentally run a different checkout's task than the
    # one its artifact was resolved from.
    gradle = _parse_gradle(data.get("gradle"), problems,
                           default_project=(jech.project if jech is not None else None))
    uitest = _parse_uitest(data.get("uitest"), problems)
    # The launch layer. There is exactly one — the checkout's own Gradle run task
    # — because the Prism instance backend was removed on 2026-10-14 (see
    # envs/README.md). An env that names no run task cannot be executed, and that
    # is a configuration error rather than a run that quietly launches nothing;
    # the only other honest state is a declared ``skip``, which says why.
    if gradle is None and skip is None:
        problems.append(
            "env.gradle is required: the Prism instance launcher was removed on 2026-10-14, so the "
            "checkout's own Gradle run task is the only launch layer. Declare "
            '{"task": ":<module>:runClient", "daemon_java": <n>} (see envs/README.md), or declare '
            "skip with the reason the row cannot run"
        )
    # The selection is handed to Gradle as a project property, so there has to be
    # a Gradle run to hand it to.
    if uitest is not None and gradle is None:
        problems.append(
            "env.uitest requires env.gradle: the scenario selection travels to the checkout as the "
            "-PjechTest project property, which only the gradle launch can pass"
        )

    if problems:
        raise EnvSchemaError(
            f"{path}: {len(problems)} schema problem(s):\n  - " + "\n  - ".join(problems)
        )

    return Env(name=name, tags=tags, mc=mc, loader=loader, java_version=java_version, jech=jech,
               mods=mods, checks=checks, timeout_sec=timeout_sec, path=path, raw=data,
               ready_when=ready_when, skip=skip, world=world, gradle=gradle, uitest=uitest)


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
            "not '.' or '..'): it becomes saves/<name> inside the game directory"
        )
        return None
    return WorldSpec(name)


def _parse_via(data, context, problems, *, default=DEFAULT_VIA):
    """``via`` — where this artifact's jar is staged. Absent means ``game-dir``."""
    value = data.get("via")
    if value is None:
        return default
    if not isinstance(value, str) or value.strip() not in MOD_VIA:
        problems.append(f"{context}.via must be one of {', '.join(MOD_VIA)}, got {value!r}")
        return default
    return value.strip()


def _parse_gradle(data, problems, *, default_project=None):
    """``env.gradle`` — present means "launch through the checkout's Gradle run task"."""
    if data is None:
        return None
    if not isinstance(data, dict):
        problems.append('env.gradle must be an object {"task": ..., "project_dir": ..., '
                        '"daemon_java": ..., "args": [...]}')
        return None
    _reject_unknown(data, {"task", "project_dir", "daemon_java", "args"}, "env.gradle", problems)
    task = _string(data, "task", "env.gradle", problems, required=True)
    project_dir = _string(data, "project_dir", "env.gradle", problems, required=False)
    if project_dir is None and "project_dir" not in data:
        project_dir = default_project
    if not project_dir:
        problems.append(
            "env.gradle.project_dir is required (the checkout directory to run gradle in, relative "
            "to $JT_JECH_ROOT) unless jech.project already names it"
        )
        project_dir = None
    elif Path(project_dir).is_absolute():
        problems.append("env.gradle.project_dir must be relative to the JECh root, not absolute")
        project_dir = None
    daemon_java = None
    if data.get("daemon_java") is not None:
        daemon_java = _int(data, "daemon_java", "env.gradle", problems, required=False, minimum=8)
    args = _string_list(data, "args", "env.gradle", problems, required=False)
    if task and project_dir:
        return GradleSpec(task, project_dir, daemon_java, args)
    return None


def _parse_uitest(data, problems):
    """``env.uitest`` — present means "run the checkout's in-client UI test harness"."""
    if data is None:
        return None
    if not isinstance(data, dict):
        problems.append('env.uitest must be an object {"selection": "tag:smoke"}')
        return None
    _reject_unknown(data, {"selection"}, "env.uitest", problems)
    # ``_string`` already refuses a missing, null or blank selection and trims it
    # (which is also why no example is repeated in its message). What is left to
    # check is that the value survives being handed to Gradle as one argv element.
    selection = _string(data, "selection", "env.uitest", problems, required=True)
    if selection is not None and any(char in selection for char in "\n\r\t\0"):
        problems.append("env.uitest.selection must be one line without control characters "
                        "(it is passed to Gradle verbatim)")
        selection = None
    if selection is None:
        return None
    return UiTestSpec(selection)


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
    _reject_unknown(data, {"source", "path", "expectModId", "slug", "describe", "pin", "project",
                           "via"},
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
    via = _parse_via(data, "env.jech", problems)
    return JechSpec(source, path, expect, slug, describe, pin, project, data, via=via)


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
        _reject_unknown(item, {"name", "source", "slug", "describe", "pin", "tags", "via"},
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
        via = _parse_via(item, context, problems)
        if name and source and slug and describe:
            mods.append(ModSpec(name, source, slug, describe, pin, item, via=via))
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
                        f"{context}.value must be relative to the run's game directory, "
                        f"got an absolute path: {absolute[0]!r}"
                    )
                    value = None
        elif type_ in CHECK_PATH_OPTIONAL_TYPES:
            # An optional path, and the only checks shaped that way: the artifact
            # they read has one well-known location in the game directory, so an
            # env may name another one but usually says nothing.
            if value is not None:
                if not isinstance(value, str) or not value.strip():
                    problems.append(f"{context}.value must be a non-empty path relative to the "
                                    f"game directory for {type_} (or omitted, to use "
                                    "the default location)")
                    value = None
                elif Path(value.strip()).is_absolute():
                    problems.append(
                        f"{context}.value must be relative to the run's game directory, "
                        f"got an absolute path: {value!r}"
                    )
                    value = None
                else:
                    value = value.strip()
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
