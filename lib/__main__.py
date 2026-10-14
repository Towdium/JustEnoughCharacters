"""The ``jt`` command line: envs, resolve, seed-world, run, verify, selftest.

The dispatch table is small on purpose. Every command either prints what it
found (``envs``, ``resolve --list``), writes a verdict (``run``, ``selftest``) or
gates on one (``verify``); the exit codes are the contract documented in
``lib.errors``.
"""

from __future__ import annotations

import argparse
import json
import sys
import unittest

from . import __version__, envs as envs_mod, instance, paths, report as report_mod, resolve
from . import runner, worlds
from .errors import JtError

EXAMPLE_TAG = "example"


def build_parser():
    parser = argparse.ArgumentParser(
        prog="jt",
        description="JECh compatibility test harness: pin mods, build a pack, run it in Prism, judge the log.",
    )
    parser.add_argument("--version", action="version", version=f"jt {__version__}")
    sub = parser.add_subparsers(dest="command", required=True, metavar="command")

    listing = sub.add_parser("envs", help="list envs with their resolution state")
    listing.add_argument("--json", action="store_true", help="print the env files as json")
    listing.set_defaults(func=cmd_envs)

    resolving = sub.add_parser("resolve", help="pin the version descriptions and download the files")
    resolving.add_argument("selection", help="env name or expression: all | name | a,b | tag:x | regex:x")
    resolving.add_argument("--exclude", default=None, help="a second expression subtracting from the first")
    resolving.add_argument("--refresh", action="store_true", help="ignore the cached API responses and re-fetch")
    resolving.add_argument("--no-download", action="store_true", help="only pin, do not download into the cache")
    resolving.add_argument("--list", dest="list_candidates", action="store_true",
                           help="print the candidate files each description resolves against, then exit")
    resolving.set_defaults(func=cmd_resolve)

    seeding = sub.add_parser(
        "seed-world",
        help="generate (or reuse) the env's world seed with the vanilla server for its mc version",
    )
    seeding.add_argument("selection", help="env name or expression: all | name | a,b | tag:x | regex:x")
    seeding.add_argument("--exclude", default=None, help="a second expression subtracting from the first")
    seeding.add_argument("--refresh", action="store_true",
                         help="rebuild the seed even when the cache verifies against its manifest")
    seeding.set_defaults(func=cmd_seed_world)

    running = sub.add_parser("run", help="build, isolate, launch and judge every selected env")
    running.add_argument("selection", help="env name or expression: all | name | a,b | tag:x | regex:x")
    running.add_argument("--exclude", default=None, help="a second expression subtracting from the first")
    running.add_argument("--refresh", action="store_true", help="re-fetch API responses and downloads")
    running.set_defaults(func=cmd_run)

    verifying = sub.add_parser("verify", help="read a run report and fail unless every check passed")
    verifying.add_argument("run_dir", help="a runs/<id> directory (or its report.json)")
    verifying.set_defaults(func=cmd_verify)

    selftesting = sub.add_parser("selftest", help="run the offline unit tests of the harness itself")
    selftesting.add_argument("--pattern", default="test_*.py", help="unittest discovery pattern")
    selftesting.add_argument("-q", "--quiet", action="store_true", help="less output")
    selftesting.set_defaults(func=cmd_selftest)
    return parser


# ---------------------------------------------------------------------------
# commands
# ---------------------------------------------------------------------------


def cmd_envs(args, log=print):
    loaded = envs_mod.load_all()
    if args.json:
        log(json.dumps([_env_json(env) for env in loaded], indent=2, ensure_ascii=False))
        return 0
    if not loaded:
        log(f"no env files under {paths.display(paths.envs_dir())}")
        return 0
    log(f"JT_HOME      {paths.display(paths.jt_home())}")
    log(f"envs dir     {paths.display(paths.envs_dir())}")
    log(f"prism root   {paths.display(paths.prism_root())}")
    log("")
    log(f"{'env':28} {'mc/loader':28} {'java':>4} {'mods':>5} {'checks':>6}  tags")
    for env in loaded:
        pinned = sum(1 for mod in env.mods if mod.pinned)
        jech_state = "local" if env.jech.source == "local-build" else (
            "pinned" if env.jech.pinned else "unresolved")
        mods = f"{pinned}/{len(env.mods)}"
        log(f"{env.name:28} {env.mod_label:28} {env.java_version:>4} {mods:>5} "
            f"{len(env.checks):>6}  {','.join(env.tags) or '-'}")
        log(f"{'':28} jech: {env.jech.source} ({jech_state})  file: {paths.display(env.path)}")
        if env.gradle is not None:
            log(f"{'':28} backend: gradle {env.gradle.task} in "
                f"{env.gradle.project_dir} (daemon_java "
                f"{env.gradle.daemon_java if env.gradle.daemon_java else 'ambient'})")
        if env.world is not None:
            log(f"{'':28} world: {env.world.name} (saves/{env.world.name}, seeded by "
                "jt seed-world)")
        if env.skip:
            log(f"{'':28} skip: {env.skip}")
        if EXAMPLE_TAG in env.tags:
            log(f"{'':28} note: tagged '{EXAMPLE_TAG}' - a schema example, not a runnable version line")
    return 0


def cmd_seed_world(args, log=print):
    """Build (or verify and reuse) the world seed every env launched into a world needs.

    An env that declares no world is a no-op, not a failure — it does not need a
    seed. But a selection in which *nothing* needs one exits 1 and says so: that
    is the same fail-closed rule the empty selection follows, because a command
    that verified nothing must not read as success.
    """
    paths.ensure_data_dirs()
    loaded = envs_mod.load_all()
    selected = _select(loaded, args.selection, args.exclude, log=log)
    if not selected:
        return 1
    wanted = [env for env in selected if env.world is not None]
    for env in selected:
        if env.world is None:
            log(f"{env.name}: declares no world, so there is nothing to seed")
    if not wanted:
        log(f"none of the {len(selected)} selected env(s) declares a world; nothing was seeded "
            "(this is never reported as a pass)")
        return 1
    failures = 0
    for env in wanted:
        try:
            seed = worlds.ensure_seed(env, java_home=lambda: instance.java_home_for(env.java_version),
                                      refresh=args.refresh, log=log)
        except JtError as exc:
            log(f"{env.name}: {type(exc).__name__}: {exc}")
            failures += 1
            continue
        log(f"  {env.name}: {seed.describe()}")
        log(f"  {env.name}: {paths.display(seed.directory)}")
    return 1 if failures else 0


def cmd_resolve(args, log=print):
    loaded = envs_mod.load_all()
    selected = _select(loaded, args.selection, args.exclude, log=log)
    if not selected:
        return 1
    failures = 0
    for env in selected:
        log(f"--- {env.name} ({env.mod_label}, java {env.java_version})")
        try:
            resolved = resolve.resolve_env(
                env, refresh=args.refresh, download=not args.no_download,
                list_only=args.list_candidates, log=log,
            )
        except Exception as exc:  # JtError plus the API/parse errors it wraps
            from .errors import JtError
            if not isinstance(exc, JtError):
                raise
            log(f"{env.name}: {type(exc).__name__}: {exc}")
            failures += 1
            continue
        if args.list_candidates:
            continue
        for artifact in resolved.artifacts:
            log(f"  {artifact.describe()}")
        if resolved.changed:
            log(f"  env file updated: {paths.display(env.path)}")
    return 1 if failures else 0


def cmd_run(args, log=print):
    return runner.run_selection(args.selection, args.exclude, refresh=args.refresh, log=log)


def cmd_verify(args, log=print):
    return report_mod.verify_run(args.run_dir, log=log)


def cmd_selftest(args, log=print):
    """Offline unittest discovery over ``selftest/``."""
    root = str(paths.repo_root())
    if root not in sys.path:
        sys.path.insert(0, root)
    suite = unittest.TestLoader().discover(
        start_dir=str(paths.repo_root() / "selftest"), pattern=args.pattern, top_level_dir=root)
    verbosity = 1 if args.quiet else 2
    result = unittest.TextTestRunner(verbosity=verbosity, stream=sys.stderr).run(suite)
    ran = result.testsRun
    problems = len(result.failures) + len(result.errors)
    log(f"selftest: {ran} test(s), {problems} problem(s)"
        + (f", {len(result.skipped)} skipped" if result.skipped else ""))
    return 0 if result.wasSuccessful() else 1


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------


def _select(loaded, expression, exclude, *, log):
    from .select import Selection
    selection = Selection.parse(expression, exclude)
    matched = selection.select(loaded, name_of=lambda env: env.name, tags_of=lambda env: env.tags)
    log(selection.explain([env.name for env in matched], len(loaded)))
    if not matched:
        log("nothing selected: an empty or unmatched selection is never a pass")
    return matched


def _env_json(env):
    return {
        "name": env.name,
        "tags": env.tags,
        "mc": env.mc,
        "loader": {"type": env.loader.type, "version": env.loader.version},
        "java": {"version": env.java_version},
        "backend": env.backend,
        "gradle": ({"task": env.gradle.task, "projectDir": env.gradle.project_dir,
                    "daemonJava": env.gradle.daemon_java} if env.gradle is not None else None),
        "jech": {"source": env.jech.source, "pinned": env.jech.pinned,
                 "expectedModId": env.jech.expect_mod_id, "path": env.jech.path,
                 "slug": env.jech.slug, "via": env.jech.via},
        "mods": [{"name": mod.name, "source": mod.source, "slug": mod.slug,
                  "describe": mod.describe, "pin": mod.pin, "via": mod.via}
                 for mod in env.mods],
        "checks": [{"type": check.type, "name": check.name, "value": check.value}
                   for check in env.checks],
        "readyWhen": ({"value": env.ready_when.value} if env.ready_when is not None else None),
        "skip": env.skip,
        "world": ({"name": env.world.name, "savesPath": env.world.saves_path}
                  if env.world is not None else None),
        "timeout_sec": env.timeout_sec,
        "jvm": {"max_mem_mb": env.jvm.max_mem_mb, "min_mem_mb": env.jvm.min_mem_mb,
                "window": list(env.jvm.window)},
        "file": paths.display(env.path),
    }


def main(argv=None):
    from .errors import JtError
    parser = build_parser()
    args = parser.parse_args(sys.argv[1:] if argv is None else argv)
    try:
        return args.func(args)
    except JtError as exc:
        print(f"jt: {type(exc).__name__}: {exc}", file=sys.stderr)
        return exc.exit_code
    except KeyboardInterrupt:
        print("jt: interrupted", file=sys.stderr)
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
