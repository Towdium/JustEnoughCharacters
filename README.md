# jt — the JECh compatibility test harness

`jt` launches Just Enough Characters together with a mod it must interoperate
with (JEI, or its 1.12.2-era forks), inside a throwaway Prism Launcher instance,
and judges the run from evidence instead of from a human reading a log: the
game's log, its crash reports, whether it exited, and the files it was supposed
to write. One env file per (version line, loader) pair describes the whole
experiment — the JECh build under test, the mods and exact versions to load, the
JDK, the JVM memory, the watchdog budget and the assertions. Everything is
reproducible from that file plus the on-disk cache, and a run whose evidence is
missing fails instead of passing quietly.

Pure Python 3, standard library only, no dependency to install; the tests that
verify the harness itself run offline.

## Usage

```bash
python3 jt envs                       # list the envs, their resolution state and tags
python3 jt envs --json                # the same as json

python3 jt resolve all                # pin the version descriptions and download the files
python3 jt resolve 1.21.1-fabric      # one env (name, or tag:x / regex:x / a,b)
python3 jt resolve tag:fabric --exclude tag:112
python3 jt resolve --list <env>       # show the candidate files a description would match
python3 jt resolve --no-download <env># pin only

python3 jt seed-world 1.21.1-neoforge # build (or reuse) the world an env is launched into

python3 jt run all --exclude tag:experimental    # build, isolate, launch, judge
python3 jt run 1.12.2-forge-jei-legacy
python3 jt run all --exclude tag:experimental --exclude tag:skip  # run only what runs

python3 jt verify runs/<id>           # re-check a run's report.json; non-zero unless it passed
python3 jt selftest                   # the harness's own offline unit tests
```

`jt run` is strictly serial on purpose: a 16 GB machine cannot host two modded
Minecraft clients at once. `jt seed-world` is serial for the same reason — it
runs a vanilla dedicated server.

## What a run does

1. **Resolve** the env (pinning anything still unresolved) and download every jar
   into the cache; the JECh artifact is used in place from its build directory.
   An env that declares **`skip`** stops right here: nothing is resolved, nothing
   is launched, and the report records `SKIP` with the reason (never a pass — see
   "States" below).
2. **Build the world seed** when the env declares `world` and the cache has none:
   the vanilla dedicated server of the env's own Minecraft version generates a
   small superflat world, which is cached (content-hashed, ~3.3 MiB for 1.21.1)
   under `<JT_HOME>/cache/worlds/<mc>-<name>`. A cached seed is re-hashed and
   reused; a rebuild lays out the same landscape but is not byte-identical
   (`level.dat` timestamps), and nothing depends on that.
3. **Render the pack** into `runs/<id>/pack` — `files` empty, every jar in
   `overrides/mods/`, plus the preset `overrides/options.txt` and, when the env
   declares `world`, `overrides/saves/<name>/`. It is kept as the run's exact
   input record.
4. **Materialize the instance** — write the env's Prism instance directly:
   `instance.cfg` (java, memory, window) plus `mmc-pack.json` (the Minecraft and
   loader components). Memory goes in `MinMemAlloc`/`MaxMemAlloc` with
   `OverrideMemory=true` and the heap stays out of `JvmArgs`: Prism treats
   `-Xms`/`-Xmx` in the JVM-args field as a manual override and answers with a
   modal warning that no headless run can dismiss. The instance is named
   deterministically after the env and its modset hash (`<env>-<hash12>`), so a
   retry lands on the same directory; if a directory with that name exists but
   `state.json` does not point at it (debris from an interrupted run), it is
   removed in python and rebuilt — never a shell `rm`, and never silently reused.
   Prism's own launch task then resolves and downloads whatever the version chain
   still needs.
   *Why not `prismlauncher -I`?* On Prism 11 a local `-I` goes through
   `MainWindow::addInstance()`, which opens the modal *New Instance* dialog, and
   the CLI keeps the main window open afterwards instead of exiting — a headless
   run can never finish it. Writing the two files Prism reads is the same import
   without its UI, and it is the only dialog-free path.
5. **Isolate**: re-sync `mods/` from the pack, archive then empty `config/`,
   empty `saves/` and `logs/`, and re-stage the pack's non-mod overrides
   (`options.txt`, `saves/<world>`) into the game directory. A stale instance can
   never influence a verdict: a world a previous run played in is replaced by the
   seed, and an `options.txt` the game rewrote is replaced by the preset.
6. **Launch** `prismlauncher -d <root> --launch <instance> [--world <name>]` and
   follow `logs/latest.log`, accepting only a file newer than the run start.
   `--world` is Prism's own *join world on launch* option; the game-argument
   passthrough (`-- --quickPlaySingleplayer …`) and the patches-JSON alternative
   are both rejected, with measurements, in [envs/README.md](envs/README.md).
7. **Watchdog** on `timeout_sec`: it kills the process tree and the run fails.
8. **Stop when ready**: an env may declare `ready_when` — a log line that means
   the game got up. Once that line *and* every `log_contains` marker are in the
   log, the runner ends the instance's process tree itself and goes straight to
   judging (`stop_reason: goal-reached`, recorded in the report), so a run does
   not have to wait for a game that will never quit on its own. That stop waits
   out a bounded settle (`SETTLE_GRACE_SEC`, 3s) first: a client keeps writing
   for a moment after the ready line — FML dumps a mod-loading crash report and
   logs `Crash report saved to …` 0.37s after `Sound engine started` (measured) —
   and killing it the instant the markers are in races that write, which is how
   the same env once PASSed and FAILed on identical inputs. If the ready line
   lands but a marker never arrives, the run is bounded by a grace period and
   then stopped too (`stop_reason: ready-timeout`), so a broken assertion becomes
   a FAIL rather than a watchdog kill. When the launcher CLI exits within a
   second — Prism handing the launch off to a Prism process that already owns the
   data root — the runner keeps watching for a bounded grace instead of reading
   it as "no session".
9. **Judge**: the env's checks plus three always-present `internal:` checks, then
   write `report.json`, `report.txt` and `junit.xml`.

## States

| state | what it means | exit code |
| --- | --- | --- |
| `PASS` | every check of the env passed | 0 |
| `FAIL` | a check failed, or the run/config was broken | 1 (check) / 2 (config) / 3 (watchdog) |
| `SKIP` | the env was **not executed** — either a declared `skip`, or a check whose precondition was missing | 1 |

A `SKIP` is never a pass and never silently green: an env with a declared `skip`
is listed with its reason in `report.txt`, counted in `totals.skipped`, given a
`<skipped>` testcase in `junit.xml`, and the selection exits 1. Hiding it takes
an explicit `--exclude tag:skip`. Skipped envs and skipped checks are different
totals (`totals.skipped` counts envs, `totals.checksSkipped` counts checks).

## Layout

Everything jt writes lives under **`JT_HOME`**, resolved in this order:

1. `$JT_HOME`, when set;
2. the workspace data directory (the `jech-compat` folder beside this workspace's
   other data, i.e. three levels above `compat-tests`) when it exists — the
   workstation layout, and the reason the cache is shared between checkouts;
3. `~/.jech-compat`.

```
<JT_HOME>/
  cache/http/    cached API responses (one json per request, no header values)
  cache/mods/    downloaded jars, reused when the sha1 still matches
  cache/servers/ vanilla server jars, one per Minecraft version (jt seed-world)
  cache/worlds/  world seeds: <mc>-<name>/world plus a manifest with per-file hashes
  prism/         the Prism data root jt imports into (never the user's own launcher)
  runs/<id>/     report.json, report.txt, junit.xml, run.json, game.log, launcher.log, pack/
  state.json     env -> instance mapping plus the modset hash that justified the reuse
  secrets.env    machine-local secrets, see below
```

Repository layout: `jt` is the entry point, `lib/` the implementation (paths,
envs, resolve, pack, options, worlds, mojang, instance, runner, checks, report,
cfapi, mrapi, net, secrets, select, modmatch, util, errors), `envs/` the test
matrix ([envs/README.md](envs/README.md) is the schema), `selftest/` the offline
tests.

## Secrets

`CF_API_KEY` is the only secret. Resolution order: the environment variable
first, then **`<JT_HOME>/secrets.env`** — a two-line dotenv file, `#` comments
allowed, holding `CF_API_KEY=...`.

* It is **never committed** and never named on a command line.
* jt never prints, logs or caches the value; error messages name the variable and
  the file, never the value, and the http cache key is a one-way digest.
* In CI the file does not exist at all: the runner injects a real `CF_API_KEY`
  environment variable (a repository secret).

## Exit codes

| code | meaning |
| --- | --- |
| `0` | every check passed |
| `1` | a check failed, an env was skipped, the selection matched nothing, or the report is missing/corrupt (fail-closed) |
| `2` | bad usage, a broken env file, an unresolvable dependency, a missing external tool |
| `3` | the run exceeded `timeout_sec` (the watchdog fired) |

A multi-env selection reports the highest code it saw. `SKIP` is never a pass: a
check whose precondition is absent (no new game session, no instance directory)
skips, an env that declares `skip` is not executed at all, and both keep the
selection red — while the `internal:` checks FAIL for exactly the missing
evidence, so a run whose evidence is absent cannot come out green.

## Environment variables

| variable | purpose |
| --- | --- |
| `JT_HOME` | the data root (see above) |
| `JT_JECH_ROOT` | the root the `jech.project` checkouts are looked up under; defaults to the directory containing `compat-tests` |
| `CF_API_KEY` | CurseForge API key (or `secrets.env`) |
| `JT_PRISM_LAUNCHER` | path to the Prism Launcher CLI when it is not on `PATH` |
| `JT_JAVA_HOME_CMD` | the JDK lookup command, `{version}` placeholder; defaults to macOS's `java_home` |
| `JT_JAVA_<N>_HOME` | an explicit JDK home per major version, for platforms without that helper |

The lookup never trusts the helper blindly: every candidate is accepted only
after the JDK's own `release` file reports the requested major version. macOS's
`java_home` needs that, because it answers to the *release string*, not the major
number — `-v 8` matches nothing and silently returns the newest installed JDK
(`-v 1.8` is the one that finds Java 8), so the Java 8 envs query `8` and then
`1.8`. Without the check, the 1.12.2 envs would launch on JDK 25 and fail at
`VerifyJavaInstall` instead of using the Java 8 they asked for.

## CI

* Each per-branch fork checkout carries `compat-tests` as a **submodule**, so a
  branch's own harness and envs travel with the branch. Set `JT_JECH_ROOT` to the
  branch checkout root (and `JT_HOME` to the runner's cache directory); with
  `jech.project` naming the branch directory, resolution finds the built jar at
  the root level. A matrix job runs one env per job.
* Ubuntu runners need a virtual display and software GL (`xvfb-run` plus
  mesa/llvmpipe): the client must actually start, and there is no GPU.
* The Prism data root must already hold a **Microsoft** account. An offline/dummy
  account **cannot** work, and this is now settled rather than a follow-up:
  Prism shows its first-run setup wizard whenever
  `!accounts->anyAccountIsValid()`, and `AccountList::anyAccountIsValid()` returns
  true only for an account whose `ownsMinecraft()` is true — which is
  `type != Offline && entitlement.ownsMinecraft`. A fabricated `{"type":"Offline"}`
  entry is parsed happily and still leaves the launcher claiming "you must log in
  with a Microsoft account", because the wizard's login page is the only page it
  has left. So a CI job needs a real logged-in account in that root (or a copy of
  one), or the run fails at launch (`internal:` checks report it, with the
  launcher log tail kept in the report).
* Only one Prism Launcher process may own a data root at a time. Prism's CLI
  hands off over a local socket to an instance already running for that root, so
  a leftover launcher from an earlier run turns the next `--launch` into a
  handoff. The runner no longer mistakes that for "no session" — it watches for
  `HANDOFF_GRACE_SEC` and records the handoff plus the other launcher's pid in
  the run notes — but a CI job should still run one env at a time and start from
  a clean root, because a handoff means the launch is owned by a process jt did
  not start.
* **World seeds and CI.** The seed is built by a vanilla *dedicated server*, so a
  CI job that runs a world-launched env needs a JDK for that env's major version
  (already required) and a writable `<JT_HOME>`: the server jar is ~52 MB per
  version, a seed is ~3.4 MB, and both are cached, so a job pays for them once if
  the cache is the runner's cache directory. `jt seed-world --exclude tag:skip`
  builds every seed the matrix needs before the game jobs start.
* The `1.21.1` rows reach their JEI marker **only with the world loaded**, which
  is why they declare `world`. The `mv-1.19.2` rows cannot: quick play needs
  1.20+, so they carry `skip` and a CI selection must decide explicitly whether to
  exclude them (`--exclude tag:skip`) or accept exit code 1 with the skips named.
  (Re-measured 2026-10-14: Prism only passes the quick-play game argument to
  versions whose profile carries `feature:is_quick_play_singleplayer`, which its
  metadata has from 1.20 on, and a world target has no fallback argument — the
  1.19.2 client reaches the title screen and nothing else. See
  [envs/README.md](envs/README.md) for the run.) The `moderner` ×4 and
  `mv-1.20.1` ×2 rows are also launched into a world and were measured the same
  way: ten envs in all stop at `goal-reached` on a real join, and only the two
  `mv-1.19.2` rows are skipped.
* **Known limitation:** a production instance logs at INFO, so DEBUG-level lines
  are not in `latest.log`. The modern line's transformer hit line is exactly such
  a line (`Transformed <class> with <Transformer>`, logged at DEBUG by the
  coremod), so how many classes JECh actually patched cannot be asserted today;
  raising the log4j level for the run is a follow-up, after which those checks can
  be added.
* **Resolved limitation (was): world load.** A run used to stop at the main menu,
  and JEI runs `registerAdvancedSearch` — the phase that logs JECh's
  `Registering JEI pinyin search storage` — only once a level exists (measured
  for JEI 15, 19, 26 and 29 across the 1.20.1/1.21.1/1.21.10/26.1.2 rows). The
  harness now generates a world seed with the vanilla server (`jt seed-world`),
  stages it as `saves/jechworld`, and launches the instance through Prism's
  `--world` option, so the whole "JEI plugin present" family is assertable: all
  eight runnable rows of that family pass on a real run
  (`stopReason: goal-reached`), the two `mv-1.19.2` rows excepted (they carry
  `skip`). See
  [envs/README.md](envs/README.md) for the rejected launch paths and the seed
  recipe.

## Env matrix

16 envs: every (version line, loader) pair, all tagged `smoke`. The
`experimental` rows have doubtful upstream version pins, `legacy` marks the
1.12.2 JEI row, and any env that declares `skip` is also tagged `skip`. Per-env
loader/java/target versions, the check families and the resolution rules are in
[envs/README.md](envs/README.md).

| version line | mc | neoforge | fabric | forge |
| --- | --- | --- | --- | --- |
| `moderner` | 1.21.10 | `21.10.64` | `0.19.2` | — |
| `moderner` | 26.1.2 | `26.1.2.59-beta` | `0.19.2` | — |
| `1211` | 1.21.1 | `21.1.215` | `0.16.14` | — |
| `mv` | 1.20.1 | — | `0.16.14` (see envs README) | `47.0.35` |
| `mv` | 1.19.2 | — | `0.14.11` | `43.2.3` |
| `mv` | 1.18.2 | — | `0.14.9` | `40.2.1` |
| `mv` | 1.16.5 | — | `0.14.9` | `36.2.39` |
| `112` | 1.12.2 | — | — | `14.23.5.2864` (HEI and legacy JEI) |

```bash
python3 jt resolve all --exclude tag:experimental && python3 jt run all --exclude tag:experimental
```

Two rows are expected to keep the matrix red, and neither is a harness failure:

* `mv-1.19.2-forge` / `mv-1.19.2-fabric` — declared `skip` (no quick-play world
  join before 1.20; re-measured 2026-10-14), so a selection without
  `--exclude tag:skip` exits 1 with the skip named.
* `mv-1.16.5-forge` — FAILs on `no-crash-reports`: the built jar declares
  `forge [36.2.41,)` in its own `mods.toml` while its checkout pins
  `forge_version=36.2.39`, so FML refuses to load the mod and dumps a crash
  report. That is a real upstream mismatch this harness exists to surface; the
  env stays on the pinned Forge rather than being raised to hide it. Details in
  [envs/README.md](envs/README.md).

## Requirements

* `python3` (3.9+), no third-party packages.
* Prism Launcher with its CLI (`JT_PRISM_LAUNCHER` if not on `PATH`), and a
  Minecraft account configured in the Prism root jt uses.
* The JDKs the envs ask for (8, 17, 21, 25) — each env names only a major
  version, and the lookup is per-platform (see the table above). The world seeder
  uses the same lookup for its dedicated server.
* Built JECh jars for the version line under test, in their checkouts.
* `CF_API_KEY` for CurseForge resolutions (JEI, fabric-api); Modrinth needs none.
* Network for two Mojang endpoints when a world seed is built
  (`piston-meta.mojang.com`); a cached seed needs none.
