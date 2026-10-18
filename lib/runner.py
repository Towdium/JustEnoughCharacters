"""``jt run``: build, isolate, launch, watch, judge.

One run per env, each in its own ``runs/<id>`` directory, executed strictly
serially — a 16 GB machine cannot host two Minecraft clients with modded
modpacks at once, so the harness never parallelises runs.

The sequence per env, all of it fail-closed:

1. resolve (auto-pin anything still unresolved) and download into the cache;
2. render the ``.mrpack`` into ``runs/<id>/pack`` (files list empty, all jars in
   ``overrides/mods``) — the run's written-down input set;
3. isolate the run's game directory (``runs/<id>/game``): re-sync ``mods/``,
   archive then clear ``config/``, clear ``saves/`` and ``logs/``;
4. launch the client — the row build's own run task, or an argv jt composes from
   a cached launch description, or a directly assembled environment — then follow
   ``<game dir>/logs/latest.log`` — only accepting a file that is newer than the
   run start, since ``latest.log`` can still be the previous session's;
5. a watchdog on ``timeout_sec`` kills the process tree and yields exit code 3;
6. evaluate the env's checks plus the always-present ``internal:`` checks, and
   write report.json / report.txt / junit.xml.

Exit codes: 0 pass, 1 a check failed, 2 a configuration or infrastructure error,
3 watchdog. A multi-env selection reports the highest severity it saw.
"""

from __future__ import annotations

import time
from pathlib import Path

from . import checks as checks_mod
from . import envs as envs_mod
from . import harness, instance, options, pack as pack_mod, paths, report as report_mod, resolve
from . import subject as subject_mod
from . import uitest_pipeline, worlds
from .errors import EXIT_FAIL, EXIT_OK, EXIT_USAGE, EXIT_WATCHDOG, ConfigError, JtError
from .launch import assemble as assemble_mod
from .launch import forgefamily
from .launch import launch as launch_mod
from .launch import loader as loader_mod
from .launch import vanilla as vanilla_mod
from .select import Selection
from .util import atomic_write_json, atomic_write_text, now_iso, now_stamp, sanitize_name, sha1_file

POLL_SEC = 0.5
LAUNCHER_EXIT_GRACE_SEC = 60
READY_GRACE_SEC = 120
SETTLE_GRACE_SEC = 3
CONFIG_ERROR = "configError"
LAUNCH_BACKENDS = ("gradle", "description")
DEFAULT_LAUNCH_BACKEND = "gradle"
DEFAULT_HEADLESS = True


class RunOutcome:
    def __init__(self, report, report_dir, results):
        self.report = report
        self.report_dir = report_dir
        self.results = results

    @property
    def exit_code(self):
        codes = [code for _entry, code in self.results]
        return max(codes) if codes else EXIT_FAIL




def run_selection(expression, exclude=None, *, envs=None, refresh=False, log=print,
                  subject_spec=None, subject_jar=None, launch_backend=DEFAULT_LAUNCH_BACKEND,
                  headless=DEFAULT_HEADLESS):
    """Run every env the selection matches; returns the process exit code."""
    paths.ensure_data_dirs()
    selection = Selection.parse(expression, exclude)
    all_envs = list(envs) if envs is not None else envs_mod.load_all()
    matched = selection.select(all_envs, name_of=lambda env: env.name, tags_of=lambda env: env.tags)

    started_at = now_iso()
    started = time.monotonic()
    run_id = f"{sanitize_name(selection.expression or 'selection')}-{now_stamp()}"
    agg_dir = paths.runs_dir() / run_id
    report = report_mod.new_report(run_id=run_id, selection=selection, started_at=started_at)
    report["selection"].update({
        "matched": [env.name for env in matched],
        "total": len(all_envs),
        "explanation": selection.explain([env.name for env in matched], len(all_envs)),
    })
    log(report["selection"]["explanation"])

    results = []
    if not matched:
        report["reason"] = "the selection matched no env, so nothing was executed"
        report_mod.finalize(report, finished_at=now_iso(), duration_ms=0)
        report_mod.write_report(agg_dir, report, log=log)
        return EXIT_FAIL

    for env in matched:
        entry, code = run_env(env, refresh=refresh, log=log,
                              subject_spec=subject_spec, subject_jar=subject_jar,
                              launch_backend=launch_backend, headless=headless)
        report["envs"].append(entry)
        results.append((entry, code))

    report_mod.finalize(report, finished_at=now_iso(),
                        duration_ms=(time.monotonic() - started) * 1000)
    report_mod.write_report(agg_dir, report, log=log)
    log(f"selection run: {paths.display(agg_dir)} — {report['status']}")
    return RunOutcome(report, agg_dir, results).exit_code


def run_env(env, *, refresh=False, log=print, selection_label=None, subject_spec=None,
            subject_jar=None, launch_backend=DEFAULT_LAUNCH_BACKEND, headless=DEFAULT_HEADLESS):
    """Run one env end to end; returns ``(report entry, exit code)``."""
    if launch_backend not in LAUNCH_BACKENDS:
        raise ConfigError(
            f"unknown launch backend {launch_backend!r}; this jt knows {', '.join(LAUNCH_BACKENDS)}")
    paths.ensure_data_dirs()
    run_id = f"{sanitize_name(env.name)}-{now_stamp()}"
    run_dir = paths.runs_dir() / run_id
    run_dir.mkdir(parents=True, exist_ok=True)
    started_iso = now_iso()
    started_wall = time.time()
    started = time.monotonic()

    entry = {
        "name": env.name,
        "envFile": paths.display(env.path),
        "runDir": paths.display(run_dir),
        "mc": env.mc,
        "loader": {"type": env.loader.type, "version": env.loader.version},
        "java": env.java_version,
        "startedAt": started_iso,
        "finishedAt": None,
        "durationMs": 0,
        "status": checks_mod.FAIL,
        "headless": bool(headless),
        "exitCodeKind": "config",
        "exitCode": None,
        "exitCodeSource": "unknown",
        "timedOut": False,
        "watchdog": False,
        "stopReason": None,
        "readyReached": None,
        "readyLineSeen": None,
        "missingMarkers": [],
        "readyWhen": env.ready_when.value if env.ready_when is not None else None,
        "skipReason": env.skip,
        "world": ({"name": env.world.name, "savesPath": env.world.saves_path}
                  if env.world is not None else None),
        "packHash": None,
        "dependencies": {},
        "mods": [],
        "isolation": None,
        "checks": [],
        "notes": [],
        "log": None,
        "launcherLog": None,
        "mrpack": None,
    }

    if env.skip:
        entry["status"] = checks_mod.SKIP
        entry["exitCodeKind"] = "skip"
        entry["checks"] = [
            checks_mod.result("internal:skip", "internal", checks_mod.SKIP,
                              f"declared skip: {env.skip}"),
        ]
        entry["notes"].append(
            f"skip: the env declares skip={env.skip!r}, so nothing was executed and nothing "
            "was verified"
        )
        log(f"SKIP {env.name}: {env.skip}")
    else:
        try:
            _execute(env, run_dir, entry, started_wall=started_wall, refresh=refresh, log=log,
                     subject_spec=subject_spec, subject_jar=subject_jar,
                     launch_backend=launch_backend, headless=headless)
        except JtError as exc:
            entry[CONFIG_ERROR] = True
            entry["notes"].append(f"{type(exc).__name__}: {exc}")
            entry["checks"] = [
                checks_mod.result("internal:run", "internal", checks_mod.FAIL, str(exc)),
            ]
            entry["status"] = checks_mod.FAIL
            entry["exitCodeKind"] = "config"
            log(f"{env.name}: {exc}")
        except OSError as exc:
            entry[CONFIG_ERROR] = True
            entry["notes"].append(f"OSError: {exc}")
            entry["checks"] = [
                checks_mod.result("internal:run", "internal", checks_mod.FAIL, f"OSError: {exc}"),
            ]
            entry["status"] = checks_mod.FAIL
            entry["exitCodeKind"] = "config"
            log(f"{env.name}: OSError: {exc}")

    duration_ms = (time.monotonic() - started) * 1000
    entry["finishedAt"] = now_iso()
    entry["durationMs"] = int(duration_ms)
    if entry.get("watchdog") or entry.get("timedOut"):
        entry["exitCodeKind"] = "watchdog"
    elif entry["status"] == checks_mod.PASS:
        entry["exitCodeKind"] = "pass"
    elif not entry.get(CONFIG_ERROR) and entry["status"] != checks_mod.SKIP:
        entry["exitCodeKind"] = "check"

    exit_code = env_exit_code(entry)
    report = _env_report(run_id, env, entry, started_iso, duration_ms, selection_label=selection_label)
    report_mod.write_report(run_dir, report, log=log)
    atomic_write_json(run_dir / report_mod.RUN_META, entry)
    log(f"[{entry['status']}] {env.name} in {duration_ms / 1000.0:.1f}s "
        f"(exit code {exit_code}, {paths.display(run_dir)})")
    return entry, exit_code


def env_exit_code(entry):
    """The per-env exit code: 0 pass, 1 failed check or a declared skip, 2 config, 3 watchdog.

    A skipped env exits 1 on purpose. The harness never reports "not verified" as
    success: a `jt run` whose selection contains a skipped env is red, and the
    report names the skip reason. Excluding the env (``--exclude``) is the way to
    a green selection, not hiding the skip behind a zero.
    """
    kind = entry.get("exitCodeKind")
    if kind == "watchdog" or entry.get("watchdog") or entry.get("timedOut"):
        return EXIT_WATCHDOG
    if kind == "config" or (entry.get(CONFIG_ERROR) and entry.get("status") != checks_mod.PASS):
        return EXIT_USAGE
    if entry.get("status") == checks_mod.PASS:
        return EXIT_OK
    return EXIT_FAIL


def _env_report(run_id, env, entry, started_iso, duration_ms, *, selection_label=None):
    selection = Selection.parse(selection_label or env.name)
    report = report_mod.new_report(run_id=run_id, selection=selection, started_at=started_iso)
    report["selection"].update({
        "matched": [env.name],
        "total": 1,
        "explanation": selection_label or f"single-env run of {env.name}",
    })
    report["envs"] = [entry]
    return report_mod.finalize(report, finished_at=entry["finishedAt"], duration_ms=duration_ms)




def _execute(env, run_dir, entry, *, started_wall, refresh, log, subject_spec=None,
             subject_jar=None, launch_backend=DEFAULT_LAUNCH_BACKEND, headless=DEFAULT_HEADLESS):
    """Resolve the env, hand it to one launch layer, then watch and judge the run.

    A subject on the command line selects the direct layer; otherwise
    ``--launch-backend`` decides, and ``gradle`` is what it decides when the flag
    is absent.
    """
    if headless:
        _reject_headless_that_cannot_land(env)
    source = env.jech.source if env.jech is not None else "the command line"
    log(f"{uitest_pipeline.STAGE_PREFIX} config: resolving {env.name} "
        f"(jech {source}, {len(env.mods)} target mod(s), {len(env.checks)} check(s))")
    resolved = resolve.resolve_env(env, refresh=refresh, download=True, log=log,
                                  include_jech=not (subject_spec or subject_jar))
    entry["mods"] = [artifact.to_json() for artifact in resolved.artifacts]
    for note in resolved.notes:
        entry["notes"].append(note)

    if subject_spec or subject_jar:
        handle = _launch_direct(env, run_dir, entry, resolved, refresh=refresh, log=log,
                                subject_spec=subject_spec, subject_jar=subject_jar, headless=headless)
    elif launch_backend == "description":
        handle = _launch_description(env, run_dir, entry, resolved, refresh=refresh, log=log,
                                     headless=headless)
    else:
        handle = _launch_gradle_backend(env, run_dir, entry, resolved, refresh=refresh, log=log,
                                       headless=headless)
    _watch_and_judge(env, entry, run_dir, handle, started_wall, log=log)


def _reject_headless_that_cannot_land(env):
    """Refuse a hidden-window run that nothing in it could hide.

    The switch only reaches the client through the in-client harness (through the
    row build's ``-PjechTestHeadless`` → ``-Djech.uitest.headless`` wiring, or
    through the ``jech.uitest.*`` properties jt sets on a Gradle-less launch). A
    row that declares no ``uitest`` block runs the checkout's own client, where
    the engine is not there to hide anything: silently opening a window would be
    a mode the run did not get, so it is a configuration error instead.
    """
    if env.uitest is None:
        raise ConfigError(
            f"{env.name}: --headless was asked for, but the row declares no uitest block, so no "
            "harness runs in it to hide the client's window"
        )
    if instance.names_real_input_mode(env.gradle.args if env.gradle is not None else ()):
        raise ConfigError(
            f"{env.name}: --headless cannot be combined with the row's "
            f"-P{instance.GRADLE_INPUT_MODE_PROPERTY}={instance.REAL_INPUT_MODE}: the real input "
            "driver moves the OS cursor and needs a visible, focused window"
        )


def _launch_gradle_backend(env, run_dir, entry, resolved, *, refresh, log,
                           headless=DEFAULT_HEADLESS):
    """The Gradle backends: a run task in a checkout or a standalone ``uitest`` build."""
    seed = None
    if env.world is not None:
        seed = worlds.ensure_seed(env, java_home=lambda: instance.java_home_for(env.java_version),
                                  log=log)
        entry["world"] = worlds.describe(env, seed)
        entry["notes"].append(
            f"world: launched into {env.world.saves_path} (seed {seed.digest[:16]}, "
            f"{len(seed.files)} file(s), {'reused' if seed.reused else 'built'} from "
            f"{seed.source.get('server', '?')})"
        )
    entry["notes"].append(
        "options: preset options.txt stages "
        + ", ".join(f"{key}={value}" for key, value, _why in options.explain(env.mc))
        + " (see lib/options.py)"
    )

    pack_result = pack_mod.build_pack(env, resolved, run_dir, seed=seed, log=log)
    entry["mrpack"] = paths.display(pack_result.mrpack_path)
    entry["packHash"] = pack_result.pack_hash
    entry["dependencies"] = pack_result.dependencies
    log(f"{env.name}: pack {paths.display(pack_result.mrpack_path)} "
        f"({len(pack_result.mod_filenames)} jar(s) in overrides/mods, files list empty, "
        f"overrides {', '.join(pack_result.extra_overrides)})")

    uitest_selection = env.uitest.selection if env.uitest is not None else None
    standalone = env.uitest is not None and env.uitest.project is not None
    jar_args = []
    if standalone:
        harness_dir = uitest_pipeline.harness_build_dir(env, log=log)
        gradle_home = instance.java_home_for(env.java_version)
        jech_jar = uitest_pipeline.ensure_jech_jar(env, resolved, java_home=gradle_home, log=log)
        jar_args = uitest_pipeline.jar_arguments(jech_jar, resolved)
        uitest_pipeline.compile_harness(env, harness_dir, jar_args, java_home=gradle_home, log=log)
        run_task = uitest_pipeline.run_task_of(env)
        checkout = harness_dir
        entry["gradle"] = {
            "task": run_task,
            "projectDir": paths.display(checkout),
            "daemonJava": env.java_version,
            "args": list(jar_args),
            "uitestSelection": uitest_selection,
        }
        log(f"{env.name}: standalone harness build {env.uitest.project!r} — "
            f"{uitest_pipeline.STAGE_PREFIX} test: launching {run_task}")
    elif env.gradle is None:
        raise ConfigError(
            f"{env.name}: no launch layer (env.gradle and env.uitest.project are both absent); the "
            "Prism instance launcher was removed on 2026-10-14"
        )
    else:
        checkout = checkout_dir(env, log=log)
        run_task = env.gradle.task
        entry["gradle"] = {
            "task": env.gradle.task,
            "projectDir": paths.display(checkout),
            "daemonJava": env.gradle.daemon_java,
            "args": list(env.gradle.args),
            "uitestSelection": uitest_selection,
        }
    minecraft_dir = instance.run_game_dir(run_dir)
    log(f"{env.name}: gradle run: " + " ".join(instance.gradle_command(
        checkout, run_task, minecraft_dir,
        world=env.world.name if env.world is not None else None, uitest=uitest_selection,
        headless=headless)
        + jar_args + (list(env.gradle.args) if env.gradle is not None else [])))
    if uitest_selection is not None:
        entry["notes"].append(
            f"uitest: scenarios selected by {uitest_selection!r}; the harness writes "
            f"{checks_mod.UITEST_REPORT_DEFAULT} and its captures under "
            f"{checks_mod.UITEST_CAPTURES_DIR}/ inside the game directory, and quits the client "
            "itself once the selection is done"
        )

    isolation = instance.isolate(env.name, resolved, run_dir, game_dir_path=minecraft_dir,
                                 pack_result=pack_result, log=log)
    entry["isolation"] = isolation.to_json()
    entry["notes"].extend(isolation.notes)

    log_path = instance.log_path_in(minecraft_dir)
    launcher_log = run_dir / "gradle.log"
    entry["log"] = paths.display(log_path)
    entry["launcherLog"] = paths.display(launcher_log)

    crash_before = crash_report_snapshot(minecraft_dir)
    if standalone:
        launcher_java = instance.java_home_for(env.java_version)
        log(f"{env.name}: gradle JAVA_HOME={paths.display(launcher_java)} "
            f"(the env's java {env.java_version} runs the harness build's Gradle)")
        extra_args = list(jar_args)
    else:
        launcher_java = gradle_java_home(env, log=log)
        extra_args = list(env.gradle.args)
    proc = instance.launch_gradle(checkout, run_task, minecraft_dir,
                                  world=env.world.name if env.world is not None else None,
                                  uitest=uitest_selection, headless=headless,
                                  java_home=launcher_java,
                                  log_path=launcher_log, extra_args=extra_args, log=log)
    return LaunchHandle(
        minecraft_dir=minecraft_dir, launcher_log=launcher_log, proc=proc,
        pids_fn=instance.GradleGameTracker(proc.pid, minecraft_dir).pids,
        crash_before=crash_before,
    )


def _launch_direct(env, run_dir, entry, resolved, *, subject_spec, subject_jar, refresh, log,
                   headless=DEFAULT_HEADLESS):
    """Assemble a real client environment from the env's description and start it.

    The order is the one the design puts on paper — subject, harness, client,
    loader, game directory, command line — and every step is cached separately
    (``cache/subject``, ``cache/harness``, ``cache/mc``, ``cache/loader``), so a
    second run of the same row re-downloads nothing and a second run of another
    row of the same Minecraft version reuses the client, its libraries and its
    assets.

    The world seed and the pack the Gradle backend still builds have no place
    here: the harness creates and pins its own level, and the input set of a run
    is a game directory of symlinks plus the ``options.txt`` fixture.
    """
    if env.uitest is None:
        raise ConfigError(
            f"{env.name}: --subject was given, but the row declares no uitest block, so there is "
            "nothing for the subject to be loaded into. Either run the row without a subject (its "
            "checkout backend) or declare what it runs."
        )
    if env.world is not None:
        raise ConfigError(
            f"{env.name}: the direct backend does not seed a world (env.world is declared); run "
            "this row through its checkout backend until the harness creates its own level there"
        )
    java_home = instance.java_home_for(env.java_version)
    log(f"{env.name}: direct launch on java {env.java_version} ({paths.display(java_home)})")

    subject = subject_mod.resolve(spec=subject_spec, jar=subject_jar, java_home=java_home, log=log)
    entry["subject"] = subject.to_json()
    entry["notes"].extend(subject.notes)

    harness_jar = harness.ensure_jar(env, subject_jar=subject.path,
                                     target_mod_jar=harness.target_mod_of(env, resolved),
                                     java_home=java_home, log=log)
    entry["harness"] = {
        "project": env.uitest.project,
        "jar": paths.display(harness_jar),
        "selection": env.uitest.selection,
    }

    client = vanilla_mod.ensure_client(env.mc, refresh=refresh, log=log)
    for note in client.notes:
        entry["notes"].append(note)
    shim = None
    if env.loader.type == "fabric" and vanilla_mod.needs_arm64_native_support(
            client.version_json.get("libraries") or []):
        shim = vanilla_mod.ensure_native_support(refresh=refresh, log=log)
        entry["notes"].append(
            f"shim: {shim.name} rides mods/ alongside the row's own jars — this version's window "
            "code dies inside Minecraft's constructor on macOS arm64 without it"
        )
    loader = loader_mod.ensure_loader(env.loader.type, env.loader.version, env.mc,
                                      refresh=refresh, log=log)
    entry["notes"].append(f"client: {client.describe()}")
    entry["notes"].append(f"loader: {loader.describe()}")

    minecraft_dir = instance.run_game_dir(run_dir)
    game = assemble_mod.assemble(
        minecraft_dir, mc=env.mc,
        mod_jars=[subject.path, *[artifact.path for artifact in resolved.mods], harness_jar,
                  *([shim] if shim is not None else [])],
        options_extra=env.uitest.options, log=log,
    )
    entry["notes"].extend(game.notes)
    remap_state = loader_mod.share_remap_state(loader, client, game_dir=minecraft_dir, log=log)
    entry["notes"].append(
        f"loader state: {paths.display(minecraft_dir)}/.fabric -> {paths.display(remap_state)} "
        "(the loader's remapped game jar and mods are shared between runs, not rebuilt per run)"
    )
    entry["notes"].append(
        f"assemble: {paths.display(game.path)} — mods/ holds {', '.join(game.mods)}; every jar and "
        "every asset library stays in the shared cache and is linked or passed by path, and .fabric "
        "is a link to the loader's shared remap state, so this directory is the run's own state and "
        "nothing else"
    )
    entry["launcherLog"] = paths.display(run_dir / "client.log")
    entry["log"] = paths.display(instance.log_path_in(minecraft_dir))
    command, _values = launch_mod.build_command(
        java=java_home / "bin" / "java", client=client, loader=loader, game_dir=minecraft_dir,
        natives_dir=client.natives_dir,
        extra_jvm=harness.jvm_args(env, minecraft_dir, headless=headless), log=log,
    )
    entry["launch"] = {
        "backend": "direct",
        "java": paths.display(java_home),
        "mainClass": loader.main_class,
        "nativesDir": paths.display(client.natives_dir),
        "assetsDir": paths.display(client.assets_dir),
        "classpathEntries": len(client.classpath) + len(loader.classpath),
    }
    entry["notes"].append(
        f"uitest: scenarios selected by {env.uitest.selection!r}; the harness writes "
        f"{checks_mod.UITEST_REPORT_DEFAULT} and its captures under "
        f"{checks_mod.UITEST_CAPTURES_DIR}/ inside the game directory, and quits the client "
        "itself once the selection is done"
    )

    crash_before = crash_report_snapshot(minecraft_dir)
    proc = launch_mod.launch(command, cwd=minecraft_dir, log_path=run_dir / "client.log", log=log)

    def pids():
        return [] if proc.poll() is not None else [proc.pid]

    return LaunchHandle(minecraft_dir=minecraft_dir, launcher_log=run_dir / "client.log", proc=proc,
                        pids_fn=pids, crash_before=crash_before)


def _launch_description(env, run_dir, entry, resolved, *, refresh, log, headless=DEFAULT_HEADLESS):
    """Start the dev client a Forge-family row's build describes.

    The build stage is the row's own Gradle build: it compiles the harness and
    writes the description jt then imports and caches. A warm cache skips it
    entirely, so the run stage is this process and nothing else. The game
    directory is the client's working directory — the description's program
    arguments carry ``--gameDir .``.
    """
    if env.uitest is None or env.uitest.project is None:
        raise ConfigError(
            f"{env.name}: --launch-backend=description needs a standalone harness build "
            "(env.uitest.project); this row declares none"
        )
    if env.world is not None:
        raise ConfigError(
            f"{env.name}: the description backend does not seed a world (env.world is declared); "
            "run this row through its Gradle backend"
        )
    java_home = instance.java_home_for(env.java_version)
    log(f"{env.name}: description launch on java {env.java_version} "
        f"({paths.display(java_home)})")

    harness_dir = uitest_pipeline.harness_build_dir(env, log=log)
    jech_jar = uitest_pipeline.ensure_jech_jar(env, resolved, java_home=java_home, log=log)
    jar_args = uitest_pipeline.jar_arguments(jech_jar, resolved)
    jar_sha1s = {"jech": sha1_file(jech_jar)}
    for artifact in resolved.mods:
        if artifact.path is not None:
            jar_sha1s[artifact.name] = sha1_file(artifact.path)

    description, info = forgefamily.ensure_launch(
        env, row_dir=harness_dir, jar_args=jar_args, jar_sha1s=jar_sha1s,
        java_home=java_home, refresh=refresh, log=log,
    )
    entry["notes"].append(f"description: {description.describe()}")
    entry["harness"] = {"project": env.uitest.project, "selection": env.uitest.selection}

    minecraft_dir = instance.run_game_dir(run_dir)
    game = assemble_mod.assemble(minecraft_dir, mc=env.mc, mod_jars=[],
                                 options_extra=env.uitest.options, log=log)
    entry["notes"].extend(game.notes)
    forgefamily.stage_presets(info, minecraft_dir, log=log)

    entry["launcherLog"] = paths.display(run_dir / "client.log")
    entry["log"] = paths.display(instance.log_path_in(minecraft_dir))
    entry["launch"] = {
        "backend": "description",
        "java": paths.display(java_home),
        "mainClass": description.main_class,
        "classpathEntries": len(description.classpath),
        "files": dict(description.files),
        "description": paths.display(info["path"]),
        "cacheHit": info["cacheHit"],
        "libraries": info["libraries"],
        "environment": sorted(description.environment),
    }
    entry["notes"].append(
        f"uitest: scenarios selected by {env.uitest.selection!r}; the harness writes "
        f"{checks_mod.UITEST_REPORT_DEFAULT} and its captures under "
        f"{checks_mod.UITEST_CAPTURES_DIR}/ inside the game directory, and quits the client "
        "itself once the selection is done"
    )

    command = description.compose(java=java_home / "bin" / "java",
                                  extra_jvm=harness.jvm_args(env, minecraft_dir, headless=headless))
    crash_before = crash_report_snapshot(minecraft_dir)
    proc = launch_mod.launch(command, cwd=minecraft_dir, log_path=run_dir / "client.log",
                             env_extra=description.environment, log=log)

    tracker = instance.GradleGameTracker(proc.pid, minecraft_dir)

    def pids():
        alive = [proc.pid] if proc.poll() is None else []
        return sorted(set(alive) | set(tracker.pids()))

    return LaunchHandle(minecraft_dir=minecraft_dir, launcher_log=run_dir / "client.log", proc=proc,
                        pids_fn=pids, crash_before=crash_before)


class LaunchHandle:
    """What a launch layer hands the shared watch/judge half of a run."""

    def __init__(self, *, minecraft_dir, launcher_log, proc, pids_fn, crash_before):
        self.minecraft_dir = Path(minecraft_dir)
        self.launcher_log = Path(launcher_log)
        self.proc = proc
        self.pids_fn = pids_fn
        self.crash_before = crash_before


def _watch_and_judge(env, entry, run_dir, handle, started_wall, *, log):
    """Follow the session to its end, then evaluate every check and write evidence.

    Identical for both launch layers: what a check reads (the game log, the game
    directory, the harness's report, exit code and crash reports) does not depend
    on how the client was started.
    """
    minecraft_dir = handle.minecraft_dir
    log_path = instance.log_path_in(minecraft_dir)
    ready_values, markers = readiness_rule(env)
    follower = instance.LogFollower(log_path, started_wall, poll=POLL_SEC)
    watch = _watch(env, handle.proc, follower, started_wall, log=log,
                   ready_values=ready_values, markers=markers, pids_fn=handle.pids_fn)

    entry["timedOut"] = watch["timed_out"]
    entry["watchdog"] = watch["timed_out"]
    entry["exitCode"] = watch["exit_code"]
    entry["exitCodeSource"] = watch["exit_code_source"]
    entry["stopReason"] = watch["stop_reason"]
    entry["readyReached"] = watch["ready_reached"]
    entry["readyLineSeen"] = watch["ready_line_reached"]
    entry["missingMarkers"] = watch["missing_markers"]
    entry["notes"].extend(watch["notes"])
    if watch["timed_out"]:
        entry["notes"].append(
            f"watchdog: the run exceeded timeout_sec={env.timeout_sec}; the process tree was killed"
        )
    elif watch["stop_reason"] in checks_mod.DELIBERATE_STOP_REASONS:
        detail = f"ready_when={env.ready_when.value!r}" if env.ready_when is not None else "no ready_when"
        entry["notes"].append(
            f"stop: the runner ended the session on purpose "
            f"(stop_reason={watch['stop_reason']}, {detail})"
        )

    if follower.text:
        atomic_write_text(run_dir / "game.log", follower.text)

    crash_after = crash_report_snapshot(minecraft_dir)
    new_crashes = sorted(crash_after - handle.crash_before)
    if new_crashes:
        log(f"{env.name}: crash report(s) during the run: {', '.join(new_crashes)}")

    ctx = checks_mod.CheckContext(
        log_text=follower.text,
        log_path=paths.display(log_path),
        minecraft_dir=minecraft_dir,
        session_started=follower.session_started,
        exit_code=watch["exit_code"],
        exit_code_source=watch["exit_code_source"],
        timed_out=watch["timed_out"],
        new_crash_reports=new_crashes,
        game_started=watch["game_started"],
        game_exited=watch["game_exited"],
        duration_sec=watch["duration_sec"],
        timeout_sec=env.timeout_sec,
        stop_reason=watch["stop_reason"],
        ready_reached=watch["ready_reached"],
    )
    entry["checks"] = checks_mod.internal_checks(ctx) + checks_mod.evaluate(env.checks, ctx)
    entry["status"] = checks_mod.overall_status(entry["checks"])
    for item in entry["checks"]:
        if item["status"] != checks_mod.PASS:
            log(f"{env.name}: {item['status']} {item['name']}: {item['detail']}")
    if not follower.session_started:
        tail = tail_of(handle.launcher_log)
        if tail:
            entry["notes"].append("launcher.log tail:\n" + tail)
            log(f"{env.name}: launcher.log tail:\n{tail}")


def checkout_dir(env, *, log=None):
    """The checkout a gradle env runs its task in (``$JT_JECH_ROOT/<project_dir>``).

    Fail-closed: a missing directory or a missing wrapper is a configuration
    error with the path in it, never a launch that quietly does nothing.
    """
    directory = resolve.jech_base_root() / env.gradle.project_dir
    if not directory.is_dir():
        raise ConfigError(
            f"{env.name}: gradle.project_dir {env.gradle.project_dir!r} is not a directory under the "
            f"JECh root ({paths.display(directory)})"
        )
    if not (directory / "gradlew").is_file():
        raise ConfigError(
            f"{env.name}: {paths.display(directory)} holds no gradlew wrapper, so the env's gradle "
            f"task {env.gradle.task!r} cannot be run there"
        )
    return directory


def gradle_java_home(env, *, log=None):
    """The JDK that runs Gradle (``env.gradle.daemon_java``), or ``None`` for the ambient one.

    Not the client's JDK: the checkout's own toolchain decides that. This is only
    for a wrapper whose Gradle is too old for the machine's default JDK.
    """
    if env.gradle.daemon_java is None:
        return None
    home = instance.java_home_for(env.gradle.daemon_java)
    if log:
        log(f"{env.name}: gradle JAVA_HOME={paths.display(home)} "
            f"(daemon_java={env.gradle.daemon_java})")
    return home


def readiness_rule(env):
    """``(ready values, required markers)`` for one env's stopping rule.

    ``ready values`` is the ``ready_when`` line (empty when the env declares
    none, in which case the runner never stops the game early). ``markers`` is
    every ``log_contains`` value: the env's own assertion of "JECh loaded", which
    must already hold before the runner considers the game ready.
    """
    ready = [env.ready_when.value] if env.ready_when is not None else []
    markers = [value for spec in env.checks if spec.type == "log_contains"
               for value in _check_values(spec)]
    return ready, markers


def _check_values(spec):
    value = spec.value
    if isinstance(value, (list, tuple)):
        return [str(v) for v in value]
    if value is None:
        return []
    return [str(value)]


def ready_line_seen(log_text, ready_values):
    """True once the env's ``ready_when`` line(s) are in the log (False with none)."""
    return bool(ready_values) and all(value in log_text for value in ready_values)


def all_markers_seen(log_text, markers):
    """True when every required ``log_contains`` marker is in the log."""
    return all(value in log_text for value in markers)


def _watch(env, proc, follower, started_wall, *, log, pids_fn, ready_values=(), markers=()):
    """Track the game until it is ready, it exits, the launcher gives up, or the watchdog fires.

    When the env declares ``ready_when`` the run gets a stopping rule: the moment
    the ready line and every ``log_contains`` marker are in the log, the runner
    ends the game's process tree itself (``stop_reason=goal-reached``) and
    moves on to judging. That stop is delayed by ``SETTLE_GRACE_SEC`` so a client
    that writes the evidence the checks read *just* after the ready line (a
    mod-loading crash report, say) is not killed before it does. If the ready
    line lands but a marker never arrives, the run is bounded by
    ``READY_GRACE_SEC`` and then stopped too (``stop_reason=ready-timeout``), so a
    broken assertion surfaces as a FAIL instead of an unbounded hang.

    ``pids_fn`` is how the launch layer says "these are the game's processes": the
    Gradle launch matches the process tree below the wrapper it started, by each
    candidate's working directory. The launcher itself is always this run's own —
    a Gradle invocation, which must not survive a run that never got a session —
    so its process tree is ended here rather than left behind.
    """
    if pids_fn is None:
        raise ConfigError("_watch needs a pids_fn: the way to name this run's game processes")
    deadline = started_wall + env.timeout_sec
    session_started = False
    game_started = False
    timed_out = False
    launcher_died_early = False
    stop_reason = None
    ready_seen_at = None
    goal_reached_at = None
    notes = []
    pids = []

    while True:
        now = time.time()
        if session_started:
            follower.poll_once()
        else:
            session_started = follower.wait_for_session(min(now + POLL_SEC, deadline), log=log)
        pids = pids_fn()
        if pids:
            game_started = True
            if proc.poll() is not None:
                launcher_died_early = True
        if session_started and ready_values:
            if ready_seen_at is None and ready_line_seen(follower.text, ready_values):
                ready_seen_at = now
            if ready_seen_at is not None:
                if all_markers_seen(follower.text, markers):
                    if goal_reached_at is None:
                        goal_reached_at = now
                    if now - goal_reached_at >= SETTLE_GRACE_SEC or not pids:
                        stop_reason = checks_mod.STOP_GOAL_REACHED
                        break
                elif now - ready_seen_at >= READY_GRACE_SEC:
                    stop_reason = checks_mod.STOP_READY_TIMEOUT
                    break
        if game_started and not pids:
            break
        if not game_started and proc.poll() is not None:
            note = (f"the launcher exited (code {proc.poll()}) with no game process; "
                    "no game session was started")
            log(f"{env.name}: {note}")
            notes.append(note)
            break
        if time.time() >= deadline:
            timed_out = True
            break
        time.sleep(POLL_SEC)

    follower.poll_once()
    was_running = bool(pids)
    if stop_reason or timed_out or was_running:
        instance.kill_process_tree(sorted(set(pids) | {proc.pid}), log=log)
    elif proc.poll() is None and not session_started:
        instance.kill_process_tree([proc.pid], log=log)
    exit_code = _reap(proc, log=log, timeout=LAUNCHER_EXIT_GRACE_SEC)
    if exit_code is None:
        instance.kill_process_tree([proc.pid], log=log)
        log(f"{env.name}: the gradle launcher outlived its grace period; its process tree was ended")
    if stop_reason:
        source = "runner"
    elif launcher_died_early:
        source = "detached"
    elif exit_code is not None:
        source = "launcher"
    else:
        source = "unknown"
    if not session_started and not timed_out and time.time() < deadline:
        log(f"{env.name}: the launcher exited before a game session started")
    if stop_reason:
        log(f"{env.name}: the runner ended the session (stop_reason={stop_reason}, "
            f"after {time.time() - started_wall:.0f}s)")
    game_exited = bool(stop_reason) or (game_started and not pids_fn())
    ready_line_reached = True if not ready_values else ready_line_seen(follower.text, ready_values)
    missing = ([value for value in markers if value not in follower.text]
               if session_started else list(markers))
    return {
        "timed_out": timed_out,
        "stop_reason": stop_reason,
        "ready_reached": True if not ready_values else (ready_line_reached and not missing),
        "ready_line_reached": ready_line_reached,
        "missing_markers": missing,
        "game_started": game_started,
        "game_exited": game_exited,
        "exit_code": exit_code,
        "exit_code_source": source,
        "duration_sec": time.time() - started_wall,
        "notes": notes,
    }


def _reap(proc, *, log, timeout):
    if proc.poll() is not None:
        return proc.returncode
    try:
        return proc.wait(timeout=timeout)
    except Exception:
        return None


def crash_report_snapshot(minecraft_dir):
    directory = Path(minecraft_dir) / "crash-reports"
    if not directory.is_dir():
        return set()
    return {entry.name for entry in directory.iterdir() if entry.is_file()}


def tail_of(path, lines=20):
    try:
        text = Path(path).read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""
    return "\n".join(text.strip().splitlines()[-lines:])
