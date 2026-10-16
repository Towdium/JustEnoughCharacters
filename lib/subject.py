"""The jar under test: what ``--subject`` means, and where it is cached.

The mod under test is deliberately *not* part of an env file. An env says what the
environment is (a Minecraft version, a loader, the mods a row is measured with);
which build of JECh is being tested is a property of the run, handed over on the
command line, and this module is the single place that turns it into an artifact:

``--subject <checkout>:<glob>``
    a checkout directory under ``$JT_JECH_ROOT`` plus a glob relative to it.
    Exactly one match is used as-is; with no match the module the glob names is
    built (``:<module>:build``, the same derivation the checkout backend used)
    and the glob is retried — a build that still produces nothing is an error,
    not something to guess around.
``--subject-jar <path>``
    a jar that already exists, for a subject that was built elsewhere.

Either way the jar's own mod id is read out of it and checked, and the file is
copied into ``cache/subject/<modid>-<sha1>/``. The run assembles from that copy,
not from the checkout: a rebuild racing the run cannot change what was tested,
and the sha1 in the cache path is the artifact the report's verdict belongs to.
"""

from __future__ import annotations

import os
import shutil
import subprocess
from pathlib import Path

from . import cache, paths
from .errors import ConfigError, DependencyError
from .resolve import jars_under, jech_base_root
from .util import sha1_file

SUBJECT_HINT = (
    "pass --subject <checkout>:<glob> (jt finds or builds the jar) or "
    "--subject-jar <path> to an already-built jar"
)


class Subject:
    def __init__(self, *, modid, sha1, path, filename, origin, built=False, notes=()):
        self.modid = modid
        self.sha1 = sha1
        # The cached copy the run assembles from (never the checkout's own file).
        self.path = Path(path)
        self.filename = filename
        # Where it came from, for the report: a spec, a path, or "built".
        self.origin = origin
        self.built = built
        self.notes = list(notes)

    def describe(self):
        return (f"{self.modid} {self.filename} sha1={self.sha1[:12]} "
                f"({'built' if self.built else 'reused'} from {self.origin})")

    def to_json(self):
        return {
            "modid": self.modid,
            "filename": self.filename,
            "sha1": self.sha1,
            "origin": self.origin,
            "built": self.built,
        }


def parse_spec(spec):
    """``<checkout>:<glob>`` → ``(checkout, glob)``, fail-closed on either half."""
    text = str(spec or "").strip()
    if not text:
        raise ConfigError(f"--subject is empty; {SUBJECT_HINT}")
    checkout, separator, pattern = text.partition(":")
    if not separator or not checkout.strip() or not pattern.strip():
        raise ConfigError(
            f"--subject {spec!r} is not '<checkout>:<glob>'; {SUBJECT_HINT}"
        )
    if Path(pattern).is_absolute():
        raise ConfigError(
            f"--subject {spec!r}: the glob must be relative to the checkout, so the run stays "
            "portable and the checkout is the only place a jar is looked for"
        )
    return checkout.strip(), pattern.strip()


def resolve(*, spec=None, jar=None, java_home=None, log=print):
    """Turn one of ``--subject``/``--subject-jar`` into a cached, verified jar."""
    if bool(spec) == bool(jar):
        raise ConfigError(
            "exactly one of --subject or --subject-jar is required for a run with a harness: "
            + SUBJECT_HINT
        )
    if jar:
        source = Path(os.path.expanduser(str(jar)))
        if not source.is_file():
            raise ConfigError(f"--subject-jar {jar} is not a file")
        built = False
        origin = str(source)
    else:
        source, built, origin = _from_spec(spec, java_home=java_home, log=log)

    modid = _mod_id(source)
    sha1 = sha1_file(source)
    destination = _cache_copy(source, modid, sha1, log=log)
    log(f"subject: {modid} {source.name} sha1={sha1[:12]} -> {paths.display(destination)}")
    return Subject(modid=modid, sha1=sha1, path=destination, filename=source.name,
                   origin=origin, built=built)


# ---------------------------------------------------------------------------
# --subject
# ---------------------------------------------------------------------------


def _from_spec(spec, *, java_home, log):
    checkout, pattern = parse_spec(spec)
    base = jech_base_root() / checkout
    if not base.is_dir():
        raise ConfigError(
            f"--subject {spec!r}: {paths.display(base)} is not a directory under "
            f"{paths.display(jech_base_root())}"
        )
    found = jars_under(base, pattern)
    if found:
        return _one(found, spec), False, f"{checkout}:{pattern}"
    module = _module_of(pattern)
    task = f":{module}:build"
    log(f"subject: no jar matches {pattern!r} in {paths.display(base)} yet — building {task}")
    _gradle(base, task, java_home=java_home, what=f"subject build {task}", log=log)
    found = jars_under(base, pattern)
    if not found:
        raise DependencyError(
            f"--subject {spec!r}: {task} finished but no jar matches {pattern!r} in "
            f"{paths.display(base)}"
        )
    return _one(found, spec), True, f"{checkout}:{pattern}"


def _one(found, spec):
    if len(found) > 1:
        listing = "\n".join(f"  {paths.display(p)}" for p in found)
        raise DependencyError(
            f"--subject {spec!r} is ambiguous, {len(found)} jars match:\n{listing}\n"
            "Narrow the glob so exactly one jar is selected."
        )
    return found[0]


def _module_of(pattern):
    """The Gradle module a glob names: its first path segment."""
    parts = [segment for segment in str(pattern).split("/") if segment]
    if len(parts) < 2:
        raise DependencyError(
            f"the subject glob {pattern!r} does not name a module directory (expected "
            "'<module>/build/libs/<jar>'), so there is no build task to run"
        )
    return parts[0]


def _gradle(directory, task, *, java_home, what, log):
    command = [str(Path(directory) / "gradlew"), task, "--no-daemon"]
    env = dict(os.environ)
    if java_home is not None:
        env["JAVA_HOME"] = str(java_home)
    log(f"stage: build: $ {' '.join(command)} (cwd {paths.display(directory)})")
    result = subprocess.run(command, cwd=str(directory), env=env, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, timeout=3600)
    if result.returncode != 0:
        tail = "\n".join(result.stdout.strip().splitlines()[-25:])
        raise DependencyError(f"{what} failed (exit {result.returncode}):\n{tail}")


# ---------------------------------------------------------------------------
# identity
# ---------------------------------------------------------------------------


def _mod_id(jar):
    """The single mod id a jar declares.

    The reader is the one the checkout backend already used to check an env's
    ``expectModId`` (mods.toml, fabric.mod.json, mcmod.info), so a jar whose
    metadata the harness cannot read is an error here rather than a run that
    tests an unnamed artifact.
    """
    from .util import jar_mod_ids
    ids = jar_mod_ids(jar)
    if not ids:
        raise DependencyError(
            f"{paths.display(jar)} declares no readable mod id (no mods.toml, fabric.mod.json or "
            "mcmod.info), so the run cannot say which mod it is testing"
        )
    if len(ids) > 1:
        raise DependencyError(
            f"{paths.display(jar)} declares several mod ids ({', '.join(sorted(ids))}); pass "
            "--subject-jar for a jar like this so the run can name the one under test"
        )
    return sorted(ids)[0]


def _cache_copy(source, modid, sha1, *, log):
    directory = cache.ensure_dir(cache.subject_dir(modid, sha1))
    destination = directory / source.name
    if destination.is_file() and sha1_file(destination) == sha1:
        cache.note(f"subject {modid}", destination, log=log, cached=True)
        return destination
    partial = destination.with_name(destination.name + ".part")
    shutil.copy2(source, partial)
    if sha1_file(partial) != sha1:
        partial.unlink(missing_ok=True)
        raise DependencyError(f"the copy of {source.name} into the cache did not verify; the cache "
                              "would have held a different jar than the one under test")
    os.replace(partial, destination)
    cache.note(f"subject {modid}", destination, log=log, cached=False)
    return destination
