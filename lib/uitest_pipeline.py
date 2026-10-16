"""The standalone uitest backend's build stages: JECh jar, harness compile.

An env whose ``uitest`` block names a ``project`` (``uitest/1.21.1-neoforge``)
runs through the three-stage pipeline: ``config`` (resolve + download, the
runner's existing first stage), ``build`` (this module: make sure the JECh jar
exists, then compile the harness build), and ``test`` (the run itself, launched
by the runner through the same ``instance.launch_gradle`` path the checkout
backend uses).

Both stages here are deliberately thin wrappers over Gradle invocations the
harness build itself owns, because the alternative — reimplementing glob/mtime
caching in Python — would only drift from what Gradle already does. What the
Python side owns instead is exactly the part Gradle cannot see: which checkout
to build, which JDK to start the wrapper with, and making every stage visible
in the run output (``stage: build …`` lines the run log keeps).

The jars under test are handed to the harness build as ``-PjechJar`` /
``-PjeiJar`` on **every** Gradle invocation, the compile included: the build's
own script fails closed at configuration time when they are missing, which is
what keeps a stale copy inside the build from ever being the thing under test.
"""

from __future__ import annotations

import os
import subprocess
import time
from pathlib import Path

from . import paths
from .errors import ConfigError, DependencyError

STAGE_PREFIX = "stage:"


def harness_build_dir(env, *, log=print):
    """The standalone harness build an env's ``uitest.project`` names.

    Fail-closed: the directory and its wrapper must exist here, where the
    missing build is named with its full path — a typo in the env file is a
    configuration error, not a launch that quietly does nothing.
    """
    directory = paths.repo_root() / env.uitest.project
    if not directory.is_dir():
        raise ConfigError(
            f"{env.name}: env.uitest.project {env.uitest.project!r} is not a directory under the "
            f"compat-tests repository ({paths.display(directory)})"
        )
    if not (directory / "gradlew").is_file():
        raise ConfigError(
            f"{env.name}: {paths.display(directory)} holds no gradlew wrapper, so the standalone "
            "harness build cannot run there"
        )
    return directory


def checkout_of(env):
    """The JECh checkout an env's jech.project names (its jars are built there)."""
    from . import resolve
    return resolve.jech_base_root() / (env.jech.project or "")


def ensure_jech_jar(env, resolved, *, java_home=None, log=print):
    """Stage ``build`` half 1: the JECh jar under test, built if it is missing.

    ``resolve`` has already globbed the checkout's ``build/libs``; when the glob
    found a jar the jar is reused as-is (the cache discipline: never rebuild what
    exists — a rebuild is what ``--refresh`` is for, and Gradle's own incremental
    build is the judge inside a build). When it found none the checkout's build
    runs once and the glob is repeated: the artifact the run tests is always the
    checkout's own output, never a copy this repository keeps.

    The checkout is only ever *built* here — never modified, never committed to
    (that is the wave plan's contract with the checkouts).
    """
    jar = resolved.jech.path if resolved.jech else None
    if jar is not None and Path(jar).is_file():
        log(f"{STAGE_PREFIX} build: JECh jar reused: {paths.display(jar)} "
            "(rebuilt only when absent)")
        return Path(jar)
    checkout = checkout_of(env)
    task = _jech_build_task(env)
    log(f"{STAGE_PREFIX} build: no JECh jar at the env's glob yet — building {task} in "
        f"{paths.display(checkout)}")
    started = time.monotonic()
    _run_gradle(checkout, [task, "--no-daemon"], java_home=java_home,
                what=f"{env.name}: JECh jar build", log=log)
    log(f"{STAGE_PREFIX} build: JECh jar build finished in {time.monotonic() - started:.0f}s")
    from .resolve import local_build_jar
    jar = local_build_jar(env)
    log(f"{STAGE_PREFIX} build: JECh jar ready: {paths.display(jar)}")
    return jar


def _jech_build_task(env):
    """The Gradle task that builds the module the env's ``jech.path`` names.

    The path's first segments are ``<module>/build/libs/...``, and every
    checkout's module build task is ``:<module>:build``; the harness build below
    compiles against whatever that task leaves in ``build/libs``.
    """
    parts = [segment for segment in env.jech.path.split("/") if segment]
    if len(parts) < 3 or parts[1] != "build" or parts[2] != "libs":
        raise ConfigError(
            f"{env.name}: env.jech.path {env.jech.path!r} does not name a module's build/libs "
            "output, so the build stage cannot derive the Gradle task to build it"
        )
    return f":{parts[0]}:build"


def compile_harness(env, harness_dir, jar_args, *, java_home=None, log=print):
    """Stage ``build`` half 2: compile the standalone harness build.

    ``classes`` (not ``build``): the run task compiles the same outputs anyway,
    but running it here puts the heavy one-time work — first-time Minecraft
    artifact setup, dependency downloads — in the build stage, where a failure is
    a build failure, and keeps the run stage to launching.
    """
    log(f"{STAGE_PREFIX} build: compiling the standalone harness build in "
        f"{paths.display(harness_dir)}")
    started = time.monotonic()
    _run_gradle(harness_dir, ["classes", "--no-daemon", *jar_args], java_home=java_home,
                what=f"{env.name}: harness build compile", log=log)
    log(f"{STAGE_PREFIX} build: harness build compiled in {time.monotonic() - started:.0f}s")


def run_task_of(env):
    """The run task of a standalone harness build.

    NeoForge-line builds name their run ``clientTests`` (the task ModDevGradle
    derives from it is ``runClientTests``); loom lines have exactly one client
    run, loom's own ``runClient``. The name is the build's own convention, so it
    is derived from the loader the env declares rather than configured per env —
    one more thing W2's ported lines do not have to restate.
    """
    return "runClientTests" if env.loader.type == "neoforge" else "runClient"


def jar_arguments(jech_jar, resolved):
    """The ``-P`` arguments that hand the jars under test to the harness build.

    ``jechJar`` is always the JECh jar; ``jeiJar`` is the env's target mod — the
    pinned JEI jar, found among the env's mods by its name — and it travels only
    when the env declares one. A line whose tested jar carries no integration for
    a target mod (the MV 1.16.5 fabric row, whose JECh jar holds no JEI code)
    declares none, and its harness build takes ``jechJar`` alone. An env that
    declares the mod but leaves its jar unresolved is still rejected here, and a
    line that does need the target mod says so in its own build
    (``requireJar('jeiJar', ...)``), so a forgotten mod entry fails in that build
    rather than silently losing the mod through this function.
    """
    jei = next((artifact for artifact in resolved.mods if artifact.name == JEI_MOD_NAME), None)
    arguments = [f"-PjechJar={jech_jar}"]
    if jei is None:
        return arguments
    if jei.path is None:
        raise ConfigError(
            f"the env's {JEI_MOD_NAME!r} mod resolved without a jar path, so there is "
            f"nothing to pass as -PjeiJar"
        )
    return arguments + [f"-PjeiJar={jei.path}"]


JEI_MOD_NAME = "jei"


def _run_gradle(directory, args, *, java_home=None, what, log=print):
    """One blocking Gradle invocation, its tail kept for the failure message.

    Used by the build stage only; the run stage launches Gradle in the background
    through ``instance.launch_gradle`` like the checkout backend does, because
    only that path carries the watchdog and the log follower.
    """
    command = [str(Path(directory) / "gradlew"), *args]
    env = dict(os.environ)
    if java_home is not None:
        env["JAVA_HOME"] = str(java_home)
    log(f"{STAGE_PREFIX} build: $ {' '.join(command)} (cwd {paths.display(directory)})")
    result = subprocess.run(command, cwd=str(directory), env=env,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, timeout=1800)
    if result.returncode != 0:
        tail = "\n".join(result.stdout.strip().splitlines()[-25:])
        raise DependencyError(f"{what} failed (exit {result.returncode}):\n{tail}")
    return result
