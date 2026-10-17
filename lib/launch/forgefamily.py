"""The Forge family's launch: the row build's own description, imported and cached."""

from __future__ import annotations

import hashlib
import os
import shutil
import subprocess
import time
from pathlib import Path

from .. import cache, paths
from ..errors import ConfigError, DependencyError
from ..util import atomic_write_text, dump_json_text, load_json, sha1_file
from . import description as description_mod

CACHE_SCHEMA = 2
DESCRIBE_SCRIPT = Path(__file__).with_name("jt-describe.gradle")
DESCRIBE_FILE = Path("build") / "moddev" / "jt-description.json"
BUILD_FILES = ("build.gradle", "settings.gradle", "gradle.properties",
               "gradle/wrapper/gradle-wrapper.properties")
HARNESS_DIRS = (Path("build") / "classes" / "java" / "main",
                Path("build") / "resources" / "main")
TEXT_SUFFIXES = (".txt", ".srg", ".tsrg", ".properties", ".xml", ".json", ".cfg")
ROLE_FLAGS = (
    ("-Dlog4j2.configurationFile=", "log4j"),
    ("-DlegacyClassPath.file=", "legacyClasspath"),
    ("-Dmixin.env.refMapRemappingFile=", "srg"),
)
GRADLE_TIMEOUT_SEC = 1800


def loader_key(env):
    return f"{env.loader.type}-{env.loader.version}-{env.mc}"


def key_of(*, row, build_files, harness_digest, jar_sha1s, loader, script_sha1):
    """The inputs that decide whether a cached description is still the right one."""
    return {
        "schema": CACHE_SCHEMA,
        "row": str(row),
        "build": dict(sorted(build_files.items())),
        "harness": harness_digest,
        "jars": dict(sorted(jar_sha1s.items())),
        "loader": loader,
        "describe": script_sha1,
    }


def key_digest(key):
    return hashlib.sha1(dump_json_text(key).encode("utf-8")).hexdigest()


def tree_digest(roots):
    """A content digest over every file below ``roots`` (a missing root counts)."""
    digest = hashlib.sha1()
    for root in roots:
        root = Path(root)
        if not root.is_dir():
            digest.update(f"missing:{root.name}\n".encode("utf-8"))
            continue
        for path in sorted(entry for entry in root.rglob("*") if entry.is_file()):
            digest.update(str(path.relative_to(root)).encode("utf-8"))
            digest.update(b"\0")
            digest.update(sha1_file(path).encode("utf-8"))
    return digest.hexdigest()


def ensure_launch(env, *, row_dir, jar_args, jar_sha1s, java_home, refresh=False, log=print):
    """Return ``(Description, info)`` for one row, describing it only on a miss."""
    row_dir = Path(row_dir)
    if env.uitest is None or env.uitest.project is None:
        raise ConfigError(f"{env.name}: the description backend needs env.uitest.project")
    loader = loader_key(env)
    directory = cache.loader_dir(env.loader.type, env.loader.version, env.mc)
    launch_path = directory / "launch.json"

    key = key_of(
        row=env.uitest.project,
        build_files={name: _sha1_or_missing(row_dir / name) for name in BUILD_FILES},
        harness_digest=tree_digest([row_dir / part for part in HARNESS_DIRS]),
        jar_sha1s=jar_sha1s,
        loader=loader,
        script_sha1=sha1_file(DESCRIBE_SCRIPT),
    )
    digest = key_digest(key)
    if not refresh:
        cached = _cached_launch(launch_path, digest, log=log)
        if cached is not None:
            return cached

    raw = _describe(env, row_dir, jar_args, java_home, directory, log=log)
    return _materialize(env, row_dir, raw, key, digest, directory, log=log)


def stage_presets(info, game_dir, *, log=print):
    """Copy the row build's preset config files into one run's game directory."""
    root = Path(info.get("presetRoot") or "")
    staged = []
    for relative in info.get("preset") or []:
        source = root / relative
        if not source.is_file():
            raise DependencyError(f"the cached preset {source} is gone; describe the row again")
        dest = Path(game_dir) / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, dest)
        staged.append(relative)
    if staged:
        log(f"assemble: preset {', '.join(staged)} staged from {paths.display(root)}")
    return staged


def _sha1_or_missing(path):
    path = Path(path)
    return sha1_file(path) if path.is_file() else "missing"


def _is_text(path):
    return Path(path).suffix.lower() in TEXT_SUFFIXES


def _cached_launch(path, digest, *, log):
    path = Path(path)
    if not path.is_file():
        return None
    try:
        data = load_json(path, what=f"launch description {path.name}")
    except Exception:  # noqa: BLE001 - a damaged cache is a miss, not a failure
        log(f"cache: description unreadable, describing again: {paths.display(path)}")
        return None
    if data.get("keyDigest") != digest:
        log(f"cache: description stale (key changed), describing again: {paths.display(path)}")
        return None
    started = time.monotonic()
    broken = _verify(data)
    if broken:
        log(f"cache: description stale ({broken}), describing again")
        return None
    elapsed = time.monotonic() - started
    libraries = data.get("libraries") or []
    cache.note(f"loader {loader_label(data)} description", path, log=log, cached=True)
    log(f"cache: loader {loader_label(data)} libraries hit: {len(libraries)} jar(s), "
        f"sha1 verified in {elapsed:.1f}s")
    try:
        description = description_mod.Description.from_json(
            data["description"], what=paths.display(path))
    except ConfigError as exc:
        log(f"cache: description invalid ({exc}), describing again")
        return None
    return description, _info(data, path, cached=True)


def _verify(data):
    for record in data.get("libraries") or []:
        path = Path(record.get("path") or "")
        if not path.is_file():
            return f"missing {paths.display(path)}"
        if sha1_file(path) != record.get("sha1"):
            return f"sha1 changed for {paths.display(path)}"
    for entry in (data.get("description") or {}).get("classpath") or []:
        if not Path(entry).exists():
            return f"missing classpath entry {paths.display(entry)}"
    for relative in data.get("preset") or []:
        if not (Path(data["presetRoot"]) / relative).is_file():
            return f"missing preset {relative}"
    return None


def _info(data, path, *, cached):
    return {
        "path": Path(path),
        "cacheHit": cached,
        "libraries": len(data.get("libraries") or []),
        "preset": list(data.get("preset") or []),
        "presetRoot": Path(data.get("presetRoot") or ""),
        "keyDigest": data.get("keyDigest"),
    }


def loader_label(data):
    loader = data.get("loader") or {}
    return f"{loader.get('type')}-{loader.get('version')}-{loader.get('mc')}"


def _describe(env, row_dir, jar_args, java_home, directory, *, log):
    """Run the row's build once and read the description it wrote."""
    staging = directory / "preset-stage"
    _reset_presets(staging, directory)
    command = [str(row_dir / "gradlew"), "-I", str(DESCRIBE_SCRIPT), "jtDescribe",
               "--no-daemon", *[str(arg) for arg in jar_args],
               f"-PjechTest={env.uitest.selection}",
               f"-PjechRunDir={staging}"]
    started = time.monotonic()
    log(f"stage: build: $ {' '.join(command)} (cwd {paths.display(row_dir)})")
    environ = dict(os.environ)
    if java_home is not None:
        environ["JAVA_HOME"] = str(java_home)
    result = subprocess.run(command, cwd=str(row_dir), env=environ, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, timeout=GRADLE_TIMEOUT_SEC)
    if result.returncode != 0:
        tail = "\n".join(result.stdout.strip().splitlines()[-25:])
        raise DependencyError(
            f"{env.name}: jtDescribe failed (exit {result.returncode}):\n{tail}")
    log(f"stage: build: description written in {time.monotonic() - started:.0f}s")
    path = row_dir / DESCRIBE_FILE
    if not path.is_file():
        raise DependencyError(f"{env.name}: the build wrote no description at {paths.display(path)}")
    raw = load_json(path, what=f"jt description {path.name}")
    if not isinstance(raw, dict):
        raise DependencyError(f"{env.name}: {paths.display(path)} is not a json object")
    return raw


def _reset_presets(staging, directory):
    shutil.rmtree(directory / "preset", ignore_errors=True)
    shutil.rmtree(Path(staging) / "config", ignore_errors=True)


def _materialize(env, row_dir, raw, key, digest, directory, *, log):
    """Import everything the description names and write the rewritten launch.json."""
    libraries_dir = directory / "libraries"
    files_dir = directory / "files"
    libraries_dir.mkdir(parents=True, exist_ok=True)
    files_dir.mkdir(parents=True, exist_ok=True)

    classpath = []
    imports = {}
    libraries = []
    imported = 0
    reused = 0
    for entry in raw.get("classpath") or []:
        source = Path(entry)
        if source.is_dir():
            classpath.append(source)
            continue
        if not source.is_file():
            raise DependencyError(
                f"{env.name}: the description names a classpath entry that does not exist: "
                f"{paths.display(source)}")
        dest, sha1, fetched = _import_library(source, libraries_dir)
        imported += 1 if fetched else 0
        reused += 0 if fetched else 1
        imports[str(source)] = dest
        libraries.append({"path": str(dest), "sha1": sha1, "size": dest.stat().st_size,
                          "source": str(source)})
        classpath.append(dest)

    text_sources = _text_files(raw, env, imports, libraries, libraries_dir)
    names = _staged_names(text_sources, files_dir)
    staged_text = {source: files_dir / names[source] for source in text_sources}
    mapping = dict(imports)
    mapping.update({str(source): dest for source, dest in staged_text.items()})
    for source in text_sources:
        text = source.read_text(encoding="utf-8", errors="replace")
        rewritten = description_mod.rewrite_paths(
            description_mod.drop_property_lines(text), mapping)
        atomic_write_text(staged_text[source], rewritten)

    jvm_args = [_stage_argument(arg, mapping, "@") for arg in raw.get("jvmArgs") or []]
    program_args = [_stage_argument(arg, mapping, "@") for arg in raw.get("programArgs") or []]
    if not raw.get("mainClass"):
        raise DependencyError(f"{env.name}: the description names no main class")

    preset = _capture_presets(directory / "preset-stage", directory)
    environment = {key: description_mod.rewrite_paths(str(value), mapping)
                   for key, value in (raw.get("environment") or {}).items()}
    description = description_mod.Description(
        java_major=raw.get("javaMajor"),
        main_class=raw["mainClass"],
        jvm_args=jvm_args,
        program_args=program_args,
        classpath=classpath,
        cwd=raw.get("cwd"),
        files=_roles(raw, mapping, staged_text),
        row=raw.get("row"),
        task=raw.get("task"),
        environment=environment,
    )
    payload = {
        "schema": CACHE_SCHEMA,
        "keyDigest": digest,
        "key": key,
        "loader": {"type": env.loader.type, "version": env.loader.version, "mc": env.mc},
        "libraries": libraries,
        "preset": preset,
        "presetRoot": str(directory / "preset"),
        "description": description.to_json(),
        "importedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
    }
    launch_path = directory / "launch.json"
    atomic_write_text(launch_path, dump_json_text(payload))
    log(f"cache: loader {loader_key(env)} libraries: {imported} imported, {reused} reused "
        f"({len(libraries)} file(s))")
    cache.note(f"loader {loader_key(env)} description", launch_path, log=log, cached=False)
    log(f"launch: description {paths.display(launch_path)} — {description.describe()}")
    return description, _info(payload, launch_path, cached=False)


def _import_library(source, libraries_dir):
    """Hardlink one jar to ``libraries/<sha1>/<its own name>``; a module's name is its file name."""
    source = Path(source)
    sha1 = sha1_file(source)
    dest = libraries_dir / sha1 / source.name
    if dest.is_file() and sha1_file(dest) == sha1:
        return dest, sha1, False
    dest.parent.mkdir(parents=True, exist_ok=True)
    temp = dest.with_name(dest.name + ".part")
    if temp.exists():
        temp.unlink()
    try:
        os.link(source, temp)
    except OSError:
        shutil.copy2(source, temp)
    if sha1_file(temp) != sha1:
        temp.unlink()
        raise DependencyError(f"importing {paths.display(source)} produced the wrong bytes")
    os.replace(temp, dest)
    return dest, sha1, True


def _absolute_paths(text):
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        for token in description_mod.split_quoted(stripped):
            for candidate in _path_parts(token):
                yield candidate


def _path_parts(token):
    values = [token]
    if "=" in token:
        values.append(token.split("=", 1)[1])
    for value in values:
        if not value.startswith("/"):
            continue
        for part in value.split(os.pathsep):
            if part.startswith("/"):
                yield Path(part)


def _text_files(raw, env, imports, libraries, libraries_dir):
    """Every text file the description names, and every jar they name in turn."""
    pending = [Path(arg[1:]) for arg in
               [*(raw.get("jvmArgs") or []), *(raw.get("programArgs") or [])]
               if arg.startswith("@")]
    text_sources = []
    seen = set()
    while pending:
        source = pending.pop(0)
        if str(source) in seen:
            continue
        seen.add(str(source))
        if not source.is_file():
            raise DependencyError(
                f"{env.name}: the description names an argument file that does not exist: "
                f"{paths.display(source)}")
        if not _is_text(source):
            raise DependencyError(
                f"{env.name}: {paths.display(source)} is not an argument file this layer can stage")
        text_sources.append(source)
        for found in _absolute_paths(source.read_text(encoding="utf-8", errors="replace")):
            if found.is_dir() or not found.is_file():
                continue
            if _is_text(found):
                if str(found) not in seen:
                    pending.append(found)
                continue
            if str(found) not in imports:
                dest, sha1, _fetched = _import_library(found, libraries_dir)
                imports[str(found)] = dest
                libraries.append({"path": str(dest), "sha1": sha1,
                                  "size": dest.stat().st_size, "source": str(found)})
    return text_sources


def _staged_names(text_sources, files_dir):
    names = {}
    used = set()
    for source in text_sources:
        name = source.name
        if name in used:
            name = f"{hashlib.sha1(str(source).encode('utf-8')).hexdigest()[:8]}-{source.name}"
        used.add(name)
        names[source] = name
    return names


def _stage_argument(argument, mapping, marker):
    if argument.startswith(marker):
        source = argument[len(marker):]
        return marker + str(mapping.get(source, source))
    return description_mod.rewrite_paths(argument, mapping)


def _roles(raw, mapping, staged_text):
    roles = {}
    for argument in raw.get("jvmArgs") or []:
        for flag, role in ROLE_FLAGS:
            if argument.startswith(flag):
                value = argument[len(flag):]
                roles[role] = str(mapping.get(value, value))
        if argument.startswith("@"):
            staged = staged_text.get(Path(argument[1:]))
            if staged is not None:
                roles["vmArgs"] = str(staged)
    for argument in raw.get("programArgs") or []:
        if argument.startswith("@"):
            staged = staged_text.get(Path(argument[1:]))
            if staged is not None:
                roles["programArgs"] = str(staged)
    return roles


def _capture_presets(staging, directory):
    staging = Path(staging)
    target = Path(directory) / "preset"
    captured = []
    for source in sorted(entry for entry in staging.rglob("*") if entry.is_file()):
        relative = source.relative_to(staging)
        if relative.name == "options.txt" and len(relative.parts) == 1:
            continue
        dest = target / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, dest)
        captured.append(str(relative))
    return captured
