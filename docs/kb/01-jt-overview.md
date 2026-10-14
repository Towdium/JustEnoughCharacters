> When-to-read: you drive the compat matrix from the CLI — selecting envs, running them, or reading a run's exit code and totals.

## Mental model

`jt` is a thin `python3` launcher over the in-repo `lib/` package: it resolves env files, downloads pinned artifacts, launches one Minecraft client per env **strictly serially**, judges checks and writes a report. Exit codes are the contract (0 pass / 1 fail / 2 usage / 3 watchdog) and "nothing was run" is never a pass. One `jt run` is resolve+download → render `.mrpack` → reuse-or-import the Prism instance → isolate → launch and follow `latest.log` → watchdog → checks + report. Anchors are relative to `<ct>` = `Project/JechGround/compat-tests/`.

## Packages & imports

- Entry point `<ct>/jt:1-21`: `#!/usr/bin/env python3`, puts its own dir on `sys.path`, hands off to `lib.__main__.main`; no absolute path is baked in (`_HERE` at `:13-15`).
- Modules under `<ct>/lib/`: `__main__`, `select`, `runner`, `envs`, `errors`, `paths`, `report`, `resolve`, `pack`, `instance`, `worlds`, `checks`, `secrets`, `cfapi`, `modmatch`, `options`, `mrapi`, `mojang`, `net`, `util` (the repo README names the same set, `<ct>/README.md:317-319`).
- Env vars: `JT_HOME`, `JT_JECH_ROOT`, `CF_API_KEY`, `JT_PRISM_LAUNCHER`, `JT_JAVA_HOME_CMD` (default macOS `java_home`), `JT_JAVA_<N>_HOME`, `JT_LSOF` (`README.md:350-360`).

## API quick reference

| Signature | Purpose | Contract |
| --- | --- | --- |
| `jt envs [--json]` | print the matrix | JT_HOME / envs dir / prism root, then one line per env (name, `mod_label`, java, `pinned/total mods`, checks, tags) plus jech source+status, backend, world, uitest, skip; `EXAMPLE_TAG="example"` envs also print "a schema example, not a runnable version line" (`lib/__main__.py:20, 79-110`) |
| `jt resolve <selection> [--exclude X] [--refresh] [--no-download] [--list]` | pin artifacts into env files | `--list` prints candidates (newest first, `LIST_LIMIT`=20) and a count, then continues: no env file written, nothing downloaded (`lib/resolve.py:390-412`; `lib/__main__.py:149-175`; `lib/modmatch.py:20`) |
| `jt seed-world <selection> [--exclude X] [--refresh]` | create/reuse world seeds | envs without `world` are a no-op; a selection with nothing to seed → exit 1 (`lib/__main__.py:112-160`) |
| `jt run <selection> [--exclude X] [--refresh]` | execute and judge envs | one `runs/<id>` dir per env, strictly serial (`lib/__main__.py:29-76`; `lib/runner.py:1-25`) |
| `jt verify <run_dir>` | re-judge a written report | fail-closed; `ReportError.exit_code = EXIT_FAIL` (`lib/report.py:280-336`; `lib/errors.py:79-82`) |
| `jt selftest [--pattern P] [-q]` | offline unit tests | 369 tests, no network; see 07 (`lib/__main__.py:186-199`) |

## Minimal example

```bash
cd Project/JechGround/compat-tests
python3 jt envs
python3 jt resolve all --exclude tag:skip --refresh
python3 jt run all --exclude tag:skip      # exit 0 pass / 1 fail / 2 usage / 3 watchdog
python3 jt verify <JT_HOME>/runs/<id>
python3 jt selftest -q
```
<!-- sample: none (data-driven JSON; no sample tree) -->

## Contracts & pitfalls

- `EXIT_OK=0` all checks passed; `EXIT_FAIL=1` failed check, missing/corrupt report, skipped env, zero-match selection (fail-closed); `EXIT_USAGE=2` usage error, bad env file, unresolvable dependency, missing tool; `EXIT_WATCHDOG=3` past `timeout_sec` (`lib/errors.py:12-22`).
- Aggregate: `RunOutcome.exit_code = max(codes)`, empty → `EXIT_FAIL`; one env: watchdog/`timedOut` → 3, `exitCodeKind=="config"` + non-PASS → 2, PASS → 0, else 1 (`lib/runner.py:78-81, 231-246`).
- **Never** pass an empty or zero-match selection: `Selection.is_empty` → "…is EMPTY: … nothing was run (this is never reported as a pass)"; zero matches → "matched 0 of N target(s) …"; `run_selection` writes the report and returns `EXIT_FAIL`; `cmd_seed_world`/`cmd_resolve` return 1 on an empty `_select` (`lib/select.py:55-57, 77-94`; `lib/runner.py:109-113`; `lib/__main__.py:125-126, 152-153`).
- Declared `skip` is a first-class result: env not run, entry status SKIP, `exitCodeKind="skip"`, `checks=[internal:skip]`, `notes` = reason, printed `SKIP <name>: <reason>`; it exits 1 — be green via `--exclude` (`lib/runner.py:175-189, 231-246`; `lib/envs.py:512-523`).
- `finalize`: PASS iff every env PASS; **zero envs → FAIL** ("no env was executed, so nothing was verified"); skips count as `unverified`/`totals.skipped`; `totals` keys: envs/passed/failed/skipped/checks/checksFailed/checksSkipped/watchdogs (`lib/report.py:38-60, 63-110`).
- Three `internal:` checks (`INTERNAL_PREFIX="internal:"`) always precede the env's own: `internal:session-started` (no new log → FAIL), `internal:watchdog` (timeout → FAIL), `internal:game-exit` (process missing or alive → FAIL). Statuses: PASS = evaluated and satisfied, FAIL = evaluated and unsatisfied, SKIP = not evaluable — **SKIP is never rendered as a pass**, and the run is PASS only when no check FAILs (`lib/checks.py:17-24, 35, 37-39, 123-150, 153-166`; `lib/runner.py:429`).
- `JT_HOME`: `$JT_HOME` if set and non-empty → `<ws>/Data/jech-compat` (computed, not literal; preferred over `~`) → `~/.jech-compat`. Inside it: `cache/http/` (one json per request), `cache/mods/`, `cache/servers/` (per-MC server jar), `cache/worlds/` (`<mc>-<name>/world` + manifest), `prism/`, `runs/`, `state.json` (env→instance map + modset hash), `secrets.env` (never committed) (`lib/paths.py:3-12, 14-24, 36-40, 58-65, 68-84, 87-142`).
- Run dir: `report.json` (machine verdict), `report.txt`, `junit.xml` (one testcase per check), `run.json`, `game.log`, `launcher.log` (`gradle.log` on the gradle backend), `pack/` and `game/`; reports are temp-file + `os.replace`, json first (`lib/report.py:1-13, 25-29, 118-134`; `lib/runner.py:225, 354, 405-406`).
- Runs are **never** parallelised (16 GB cannot run two clients); the log follower takes only a file newer than the run start; isolation re-syncs `mods/`, archives and empties `config/`, empties `saves/` and `logs/` (`lib/runner.py:1-25, 348`).
- Backend swap: a `gradle` block skips the Prism instance for a Gradle run task in the checkout; `minecraft_dir = instance.run_game_dir(run_dir)` = `<runs>/<id>/game` (`lib/runner.py:305-345`; `lib/instance.py:234-242`).
- Prism instance id is deterministic: `sanitize_name(f"{env.name}-{digest[:12]}")` over the modset hash (sha256 of mc/loader/java/jvm min-max/mods `filename+sha1+size`) → one (env, modset) = one directory, idempotent import; reuse needs `!refresh`, matching `instance_id`/`modset_hash` in `state.json`, an `instance.cfg` (`lib/instance.py:460-491`; `lib/pack.py:102-119`).
- Artifacts are reused unless `--refresh`; else `download_urls` in order — pin `downloadUrl` → CF CDN `mediafilez.forgecdn.net/files/<id//1000>/<id%1000:03d>/<filename>` → Modrinth `/version/<versionId>` — sha1-verified; all failed → `DependencyError` naming every URL's error (`lib/resolve.py:322-382`; `lib/cfapi.py:38-45`).
- `CF_API_KEY` is the only secret: environment first, then `<JT_HOME>/secrets.env` (dotenv). Loading is lazy and additive; an existing env var wins; only names are reported; values are never printed/logged/cached/echoed; a missing file is not an error; with neither source `SecretMissingError` says "jt never prints, logs, caches or accepts a value on the command line" (`lib/secrets.py:22-23, 26-51, 54-94`; `lib/cfapi.py:19, 109-122`).
- [defer] End-to-end run behaviour (does step 5 reach `goal-reached`; does Prism accept a staged seed) and the seed lifecycle (`worlds.ensure_seed` before pack, reused per (mc, world) name) are runtime conclusions; source-side checks: `grep -n 'never parallelises' <ct>/lib/runner.py`, `grep -n 'never reported as a pass' <ct>/lib/__main__.py` (`lib/runner.py:1-25, 272-286`; `lib/__main__.py:112-160`; `lib/worlds.py:1-34, 238-257`).

## See also

- 02 — env schema; 03 — the harness the gradle backend drives; 07 — gate, trust layers, known boundaries.
- `<ct>/README.md` §exit codes `:335-348`, §data layout `:304-315`; `envs/README.md` §Selection grammar `:580-602` (env-side selection; the scenario-side grammar is 05).
