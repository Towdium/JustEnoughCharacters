"""The in-client harness: its build, and what a run tells it to do.

The harness is one mod, built by one Gradle project per (loader, Minecraft
version) — ``uitest/<mc>-<loader>``. Two halves of its contract live here:

**its jar.** ``build`` produces the artifact a *production* loader loads (for a
loom line, the remapped jar), which is what the direct launcher puts in the
game directory's ``mods/``. A Forge-family row loads the harness as a *dev* mod
instead — ``build/classes/java/main`` plus ``build/resources/main``, named by
``-Dfml.modFolders`` in the description its build publishes — so that backend
never uses this jar. The build is handed the jars under test as project
properties (``-PjechJar`` and, where the row has one, ``-PjeiJar``) because its
scripts fail closed without them: the harness compiles against the subject's
classes, and a harness built against a stale copy would be testing something
other than the artifact the run names.

**its run configuration.** The engine reads system properties with the prefix
``jech.uitest.`` (``RunConfig.fromSystemProperties``) and does nothing at all
unless ``jech.uitest.run`` is set. Under the checkout backend those properties
came from the Gradle run wiring; with Gradle gone from the run they are JVM
arguments this module spells, which is the whole of the harness's run-time
configuration: the selection, the directory its report and captures land in
(``<game dir>/uitest``, which is where ``checks.uitest_report`` reads it), and
whether the client's window is hidden.
"""

from __future__ import annotations

import os
import subprocess
from pathlib import Path

from . import checks, paths
from .errors import ConfigError, DependencyError

PROPERTY_PREFIX = "jech.uitest."
NON_RUNTIME_SUFFIXES = ("-sources.jar", "-javadoc.jar", "-dev.jar")
TARGET_MOD_NAME = "jei"


def project_dir(env):
    """The harness build an env's ``uitest.project`` names, fail-closed."""
    directory = paths.repo_root() / env.uitest.project
    if not directory.is_dir():
        raise ConfigError(
            f"{env.name}: uitest.project {env.uitest.project!r} is not a directory under the "
            f"compat-tests repository ({paths.display(directory)})"
        )
    if not (directory / "gradlew").is_file():
        raise ConfigError(
            f"{env.name}: {paths.display(directory)} holds no gradlew wrapper, so the harness "
            "cannot be built there"
        )
    return directory


def properties(env, game_dir, *, headless=False):
    """The ``jech.uitest.*`` properties one run sets.

    Only what the env actually declares: the selection and the output directory,
    plus the hidden-window switch when the run asked for one. Everything else
    keeps the engine's own default, so a run's configuration is the row's, not
    this module's opinion.
    """
    out_dir = Path(game_dir) / checks.UITEST_REPORT_DIR
    values = {
        f"{PROPERTY_PREFIX}run": env.uitest.selection,
        f"{PROPERTY_PREFIX}out": str(out_dir),
    }
    if headless:
        values[f"{PROPERTY_PREFIX}headless"] = "true"
    return values


def jvm_args(env, game_dir, *, headless=False):
    """The same properties as ``-D`` arguments, the form the client receives."""
    return [f"-D{key}={value}" for key, value in properties(env, game_dir, headless=headless).items()]


def ensure_jar(env, *, subject_jar, target_mod_jar=None, java_home=None, log=print):
    """Build (or reuse) the harness jar an env runs; returns its path.

    ``build`` is asked for rather than ``classes``: this is the artifact a
    production loader loads from the game directory's ``mods/`` (remapped, refmap
    and access transformer included). The Forge-family description backend does
    not call this: it runs the harness as a dev mod directory.
    """
    directory = project_dir(env)
    arguments = ["build", "--no-daemon", f"-PjechJar={Path(subject_jar)}"]
    if target_mod_jar is not None:
        arguments.append(f"-P{jar_property(env)}={Path(target_mod_jar)}")
    log(f"stage: build: harness {_display(env)} — {' '.join(arguments)}")
    _gradle(directory, arguments, java_home=java_home, what=f"{env.name}: harness build", log=log)
    return _jar_of(directory, env)


def jar_property(env):
    """The property an env's target mod travels under."""
    return f"{TARGET_MOD_NAME}Jar"


def target_mod_of(env, resolved):
    """The env's target mod jar, or ``None`` when the row declares no such mod."""
    for artifact in resolved.mods:
        if artifact.name == TARGET_MOD_NAME:
            if artifact.path is None:
                raise ConfigError(
                    f"{env.name}: the {TARGET_MOD_NAME!r} mod resolved without a jar path, so the "
                    "harness build has nothing to compile against"
                )
            return artifact.path
    return None


def _jar_of(directory, env):
    """The one runtime jar a harness build leaves in ``build/libs``."""
    found = [c for c in sorted((directory / "build" / "libs").glob("*.jar"))
             if not c.name.endswith(NON_RUNTIME_SUFFIXES)]
    if not found:
        raise DependencyError(
            f"{env.name}: the harness build in {paths.display(directory)} produced no jar under "
            "build/libs, so there is nothing for the game to load"
        )
    if len(found) > 1:
        listing = "\n".join(f"  {paths.display(p)}" for p in found)
        raise DependencyError(
            f"{env.name}: the harness build left {len(found)} runtime jars in build/libs; which one "
            f"the run should load is not decidable from here:\n{listing}"
        )
    return found[0]


def _display(env):
    return env.uitest.project


def _gradle(directory, args, *, java_home, what, log):
    """One blocking Gradle invocation, its tail kept for the failure message."""
    command = [str(Path(directory) / "gradlew"), *args]
    env = dict(os.environ)
    if java_home is not None:
        env["JAVA_HOME"] = str(java_home)
    log(f"stage: build: $ {' '.join(command)} (cwd {paths.display(directory)})")
    result = subprocess.run(command, cwd=str(directory), env=env, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, timeout=1800)
    if result.returncode != 0:
        tail = "\n".join(result.stdout.strip().splitlines()[-25:])
        raise DependencyError(f"{what} failed (exit {result.returncode}):\n{tail}")
    return result
