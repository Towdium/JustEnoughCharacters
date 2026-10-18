# jt — the JECh compatibility test harness

`jt` launches Just Enough Characters together with a mod it must interoperate
with (JEI, or its 1.12.2-era forks) — through each row's own standalone harness
build under `uitest/`, which loads the jars under test, drives the client and
quits it — and
judges the run from evidence instead of from a human reading a log: the
game's log, its crash reports, whether it exited, and the files it was supposed
to write. One env file per (version line, loader) pair describes the whole
experiment — the JECh build under test, the mods and exact versions to load, the
JDK, the watchdog budget and the assertions. Everything is
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

python3 jt seed-world 1.21.1-fabric   # build (or reuse) a world seed for an env declaring world

python3 jt run all --exclude tag:experimental    # build, isolate, launch, judge
python3 jt run 1.21.1-neoforge        # one env by name (a row that really runs)
python3 jt run all --exclude tag:experimental,tag:skip  # run only what runs

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
3. **Render the pack** into `runs/<id>/pack` — `files` empty, every artifact that
   is not `via: dev-classpath` in `overrides/mods/` (none today), plus the preset
   `overrides/options.txt` and, when the env
   declares `world`, `overrides/saves/<name>/`. It is kept as the run's exact
   input record.
4. **Own the game directory** — the row's run task is pointed at
   `runs/<id>/game` through the project property `-PjechRunDir`, so every run
   reads and writes a game directory of its own. Nothing else is materialized:
   the instance launcher that used to write an `instance.cfg` and an
   `mmc-pack.json` here was removed on 2026-10-14 (see "The `gradle` backend"),
   and the env's memory/window keys went with it — a development run's JVM is
   the build's own.
5. **Isolate**: re-sync `mods/` from the pack, archive then empty `config/`,
   empty `saves/` and `logs/`, and re-stage the pack's non-mod overrides
   (`options.txt`, `saves/<world>`) into the game directory. A stale directory
   can never influence a verdict: a world a previous run played in is replaced
   by the seed, and an `options.txt` the game rewrote is replaced by the preset.
   Only artifacts with `via: game-dir` are staged — see "The `gradle` backend"
   for the other value.
6. **Launch** `./gradlew <run task> --no-daemon -PjechRunDir=<game dir>
   -PjechTest=<selection> -PjechJar=<jar> [-PjeiJar=<jar>]` in the row's own
   harness build (`uitest/<row>`; `jt` compiles it and, when the checkout has no
   jar yet, builds the JECh jar first) and follow the game directory's
   `logs/latest.log`, accepting only a file newer than the run start. The client
   is named by its **working directory** (`lsof`), because Gradle hands the
   client its arguments through an `@argfile` — the full contract is in "The
   `gradle` backend".
7. **Watchdog** on `timeout_sec`: it kills the process tree and the run fails.
8. **Stop when ready**: an env may declare `ready_when` — a log line that means
   the game got up. Once that line *and* every `log_contains` marker are in the
   log, the runner ends the session's process tree itself and goes straight to
   judging (`stop_reason: goal-reached`, recorded in the report), so a run does
   not have to wait for a game that will never quit on its own. That stop waits
   out a bounded settle (`SETTLE_GRACE_SEC`, 3s) first: a client keeps writing
   for a moment after the ready line — FML dumps a mod-loading crash report and
   logs `Crash report saved to …` 0.37s after `Sound engine started` (measured) —
   and killing it the instant the markers are in races that write, which is how
   the same env once PASSed and FAILed on identical inputs. If the ready line
   lands but a marker never arrives, the run is bounded by a grace period and
   then stopped too (`stop_reason: ready-timeout`), so a broken assertion becomes
   a FAIL rather than a watchdog kill. When the launcher exits while the client it
   started keeps running — Gradle may hand the client to another process — the
   runner does not read that as "no session": it keeps watching, and records the
   game's own exit code as unobservable (`exit_code_source: detached`).
9. **Judge**: the env's checks plus three always-present `internal:` checks, then
   write `report.json`, `report.txt` and `junit.xml`.

## The `gradle` backend

Every row is launched by Gradle, but not by the checkout's own run task any more:
each row has a **standalone harness build** — an independent Gradle project under
`uitest/` in this repository, named by the env's `uitest.project` — and the runner
compiles it, then drives its run task. That build loads the jars under test from
paths the runner hands it, drives the client and quits it, so the checkout's
development run is not on the path at all (the Prism instance launcher, the layer
before it, was removed on 2026-10-14; see [envs/README.md](envs/README.md),
"`gradle` — a launch layer no row uses"). Everything outside the launch layer is unchanged
— resolution, downloads, isolation, the watchdog, the log follower and the checks
— and the report records the run task and the directory it ran in
(`gradle.task`, `gradle.projectDir`).

A **`gradle`** block — a run task inside the checkout under test — is still part
of the schema (`lib/envs.py:67`, `lib/envs.py:516`) and the runner still runs one
when an env declares it (`lib/runner.py:316`), but no env does: a `gradle` block
and `uitest.project` are mutually exclusive, and a row that names neither is a
schema error rather than a run that quietly launches nothing
(`lib/envs.py:312`, `lib/envs.py:326`).

```json
"gradle": {
  "task": ":neoforge:runClient",
  "project_dir": "1.21.1",
  "daemon_java": 21
}
```

* **The build the `uitest` block names is the switch.** An env whose `uitest`
  names a `project` runs that build's own run task — `runClientTests` on a
  NeoForge line, `runClient` on the loom and Forge lines (`lib/uitest_pipeline.py`,
  `run_task_of`) — in `uitest/<row>`, and the jars under test travel as the
  project properties `-PjechJar` and `-PjeiJar`. The build fails closed when
  either is missing, so a stale copy inside it can never become the thing under
  test. The env's `java` version is the JDK that starts this Gradle; the
  *client's* JDK is the build's own toolchain. An env that declares a `gradle`
  block instead names a run task in the checkout, run as `./gradlew <task>` in
  `<JT_JECH_ROOT>/<project_dir>`; `project_dir` defaults to the env's
  `jech.project`, so the task always runs in the checkout the artifact under test
  was built from.
* **`--no-daemon`** on every invocation: a resident Gradle daemon would keep a
  several-hundred-megabyte JVM alive between runs, and the point of the backend
  is that a run's cost ends with the run.
* **`daemon_java` is the JDK that runs Gradle** in a `gradle` row, not the
  client's: the client's JDK comes from the checkout's own toolchain. Set it only
  when the wrapper cannot run on the machine's default JDK; `null`/absent keeps
  the ambient `JAVA_HOME`. The lookup is the
  same `/usr/libexec/java_home` one the client's per-env JDK uses, and the answer
  is verified against the JDK's own `release` file. A standalone harness row has
  no such key: its Gradle starts on the env's `java` version.
* **The game directory is `runs/<id>/game`**, handed to the run build as the
  project property `-PjechRunDir`. Its run configuration turns it into
  `gameDirectory` (NeoForge's ModDevGradle) or `runDir` (Fabric Loom) — with Loom
  it is passed as a path *relative* to the module, because Loom joins the setting
  onto the module directory rather than replacing it. That directory is also the
  client's **working directory**, which is how the runner finds the game in the
  process table: the client's own command line cannot carry it — ModDevGradle
  hands its JVM and program arguments over through `@argfile` files and Fabric
  Loom's DevLaunchInjector through a config file it reads by path, so neither
  loader's game directory is visible in the process table — so the identity is
  "the java process under this launcher whose cwd is this run's game directory",
  a path only this run has, never a name. Reading a cwd needs `lsof` (`$JT_LSOF`
  to relocate it). Both wirings are **property-guarded**: without the property a
  hand-run `./gradlew` works in the build's own `runs/` directory instead.
* **The world** travels the same way for a `gradle` row, as
  `-PjechQuickPlayWorld`; the checkout adds `--quickPlaySingleplayer <world>` to
  the client's program arguments, and the world must already be staged in the
  game directory (isolation does that from the pack's overrides). There is no
  launcher-side `--world` to lean on here (the instance launcher that offered one
  was removed on 2026-10-14). No env declares `world` today: a standalone harness
  build carries no such property, because its own runner creates and pins the
  level its scenarios are asserted against and quits the client when they are
  done.
* **Mods: `via`.** Each artifact (the mod under test and each `mods[]` entry) may
  carry `"via"`, which decides whether its jar is staged into the game
  directory's `mods/`:
  * `game-dir` (**default**) — the jar is copied from the cache. Use it for
    anything the run does not already carry on its classpath.
  * `dev-classpath` — nothing is staged; the artifact is still resolved, pinned
    and reported, and the run loads it itself — a standalone harness build from
    the path the runner hands it, a `gradle` row from a dependency its checkout's
    own run declares (the module's sources registered as the mod, or
    `localRuntime` / `modImplementation`). Staging it anyway would put **two
    copies of one mod id** in front of the loader.

  Every artifact of every row is `dev-classpath` today, and a standalone harness
  row has no choice: the schema rejects `game-dir` there (`lib/envs.py:332`),
  because the harness build loads every jar itself, so a staged copy would
  double-load. The per-row `via` stripes the matrix was written with — which
  module declares JEI `localRuntime`, which fabric-api `modApi`, which row passed
  `-PdevMod` — belong to the checkout-task shape the rows have left;
  `selftest/test_envs.py` asserts that every artifact is `dev-classpath` today.
* A launcher is always cleaned up: if it never produces a session, or outlives
  its exit grace, its process tree is ended — a Gradle daemon plus a client it
  keeps alive is exactly the memory this machine does not have.
* **The client needs a live display.** The client opens a real window, so if the
  machine's display has gone to sleep mid-run the client dies at startup with
  `Failed to find a primary monitor` / `glfwGetPrimaryMonitor failed` on
  `EARLYDISPLAY`, `runClient` exits 1, and the env FAILs on its markers. That is
  an environment condition, not a verdict on JECh: re-run with the display awake.
  The failure is recognisable from the report's `exitCodeKind` (`check`, source
  `launcher`, exit code 1 with no session) plus the `EARLYDISPLAY` line in the
  game log — this is why the harness records the log next to every report.
* **A run hides that window by default.** `jt run` — a single env and a `run all`
  selection alike — hands the row build `-PjechTestHeadless`, which becomes
  `-Djech.uitest.headless=true` (a Gradle-less
  `--subject`/`--launch-backend=description` launch gets the property directly),
  so the client hides its window as soon as it has one instead of holding the
  user's focus for the 30-90 seconds it spends working. `--headless` says the same
  thing explicitly. The scenarios and their screenshots are unaffected, the
  report records `headless` per env, and a row that declares no `uitest` block is
  refused rather than quietly run with a window.
* **`jt run --windowed` is the way back to a visible client**, and the only way a
  row that asks for the real input driver (a row whose `gradle.args` name
  `-PjechTestInputMode=REAL`) can run at all: jt refuses that combination under
  the hidden-window default, before any row build sees it. A windowed run needs
  the display awake for its whole duration. `--windowed` and `--headless` are
  mutually exclusive.

## The in-client UI test harness (`uitest`)

Every row declares an **`uitest`** block, which runs an in-client UI test harness
— a dev-only mod that drives the real client through scripted scenarios and
writes its own verdict — and judges the run by that verdict instead
of by the log alone. It is the strongest thing a row can assert about a *client*:
the scenarios type into JECh's search path and assert on what the engine actually
matched, with screenshots as the evidence to look at.

```json
"uitest": { "selection": "tag:smoke,tag:jei", "project": "uitest/1.21.1-neoforge" }
```

* **The block is the switch, and the build it names is where it lands.** The
  selection travels to the run as the project property `-PjechTest=<selection>`
  (the only gate on that wiring: without it the run loads no harness mod at all),
  and every row's `uitest` names a `project` — its own standalone harness build —
  so the run task is that build's own (`runClientTests` on a NeoForge line,
  `runClient` on the loom and Forge lines). It honours `-PjechRunDir` exactly as
  any other run of that build does. A `uitest` block that names neither a
  `project` nor a `gradle` block is refused by the schema: there would be no run
  to pass the selection to. Every term of the selection carries its own prefix
  (`tag:smoke,tag:jei`), because a bare term is a scenario-*name* match.
* **The client ends itself**, so a row declares **no `ready_when`**: the harness's
  runner quits the game when the selection is done, and a stopping rule would end
  the session mid-scenario and leave no report. It declares **no `world`** either:
  the level the scenarios are asserted against is the one that same runner has just
  created. Both absences are the correct shape for such a row and are
  asserted in `selftest/test_envs.py`.
* **The report is the evidence.** The harness writes `report.json`, `report.txt`,
  `junit.xml` and `screenshots/<scenario>/` into `<game dir>/uitest` before it
  quits; the `uitest_report` check reads the report and FAILs on anything that is
  not a `PASS` with zero failed and errored scenarios — including a report that is
  missing, unreadable, or of a schema this check does not know. The log checks
  stay as well: they say the mod loaded.
* **A failing scenario is red twice over.** The harness exits non-zero when a
  scenario failed, so the harness build's own `verifyJechUiTest` fails the Gradle
  build, and `uitest_report` fails on the report, naming the scenario that failed.

`envs/README.md` has the block's schema and the rows' selections; the scenario
vocabulary is documented where the scenarios live, next to the harness build's own
copy of them (`uitest/<row>/src/main/resources/uitest/scenarios/README.md`, and
the KB's `03-uitest-harness.md` / `04-match-recording.md` for the 1.12.2 line's
copy of it).

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
  runs/<id>/     report.json, report.txt, junit.xml, run.json, game.log, launcher.log, pack/
                 and game/ (the run's game directory) plus gradle.log
  secrets.env    machine-local secrets, see below
```

Repository layout: `jt` is the entry point, `lib/` the implementation (__init__,
__main__, cache, cfapi, checks, envs, errors, harness, instance, launch/ (a package),
modmatch, mojang, mrapi, net, options, pack, paths, report, resolve, runner, secrets,
select, subject, uitest_pipeline, util, worlds), `envs/` the test matrix
([envs/README.md](envs/README.md) is the schema), `selftest/` the offline tests.

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
check whose precondition is absent (no new game session, no game directory)
skips, an env that declares `skip` is not executed at all, and both keep the
selection red — while the `internal:` checks FAIL for exactly the missing
evidence, so a run whose evidence is absent cannot come out green.

## Environment variables

| variable | purpose |
| --- | --- |
| `JT_HOME` | the data root (see above) |
| `JT_JECH_ROOT` | the root the `jech.project` checkouts are looked up under; defaults to the directory containing `compat-tests` |
| `CF_API_KEY` | CurseForge API key (or `secrets.env`) |
| `JT_JAVA_HOME_CMD` | the JDK lookup command, `{version}` placeholder; defaults to macOS's `java_home` |
| `JT_JAVA_<N>_HOME` | an explicit JDK home per major version, for platforms without that helper |
| `JT_LSOF` | the `lsof` binary, used to read a Gradle-launched client's working directory |

The lookup never trusts the helper blindly: every candidate is accepted only
after the JDK's own `release` file reports the requested major version. macOS's
`java_home` needs that, because it answers to the *release string*, not the major
number — `-v 8` matches nothing and silently returns the newest installed JDK
(`-v 1.8` is the one that finds Java 8), so the Java 8 envs query `8` and then
`1.8`. Without the check, the 1.12.2 envs would launch on JDK 25 and fail at
`VerifyJavaInstall` instead of using the Java 8 they asked for.

## CI

Each branch carries the same workflow,
`<branch>/.github/workflows/compat-tests.yml`, and **that file owns the CI story**:
read it in the branch checkout (`1.21.1/.github/workflows/compat-tests.yml` and
its three siblings) for the authoritative, per-branch version of everything
below. It runs on a **hosted `ubuntu-latest` runner** — there is no self-hosted
machine and no launcher: a run builds the modules under test and compiles each
row's own harness build, which is what starts the client, so all it needs is a
JDK, Gradle, a display and the harness's data root. The display is `xvfb-run`'s
(software GL, `-screen 0 1920x1080x24` — GLFW asks for a real colour depth), and
**no account is needed anywhere**: a Gradle dev run authenticates as the dev
player, which is why the launcher/account half of this chapter is gone.

What the three client-running workflows (`1.21.1`, `moderner`,
`multiple-version`) do; `1.12.0` is the exception described below:

* Check the branch out **under its own `jech.project` name** (`actions/checkout`
  with `path: 1.21.1` / `moderner` / `multiple-version` / `1.12.0`) with
  `submodules: recursive`, and set `JT_JECH_ROOT` to the workspace root — the
  `$JT_JECH_ROOT/<project_dir>` layout the harness resolves a row's checkout in.
  A branch's own harness and envs ride along as the `compat-tests` submodule.
* Pin `JT_HOME` to `<workspace>/jt-home`: the `Data/jech-compat` root the lookup
  would try first is computed from the checkout's own location and only exists on
  the workstation, so a hosted runner has to pin the variable.
* Provision the JDKs the branch needs and pin `JT_JAVA_<N>_HOME`, because off
  macOS the harness resolves a per-env JDK from `JT_JAVA_<major>_HOME` and then
  `JAVA_HOME`, re-checking the answer against the JDK's own `release` file:
  `1.21.1` → 21, `moderner` → 21 (the 1.21.10 toolchain and the world-seed
  server) and then 25 (the 26.1.2 client), `multiple-version` → 17.
* Cache `JT_HOME/cache`, the Gradle User Home, and — on the NeoForge branches —
  ModDevGradle's `caches/neoformruntime/intermediate_results` separately, keyed
  by the Minecraft/loader inputs rather than by the branch's sources.
* Build the modules under test through Gradle before running, so a compile error
  fails with a Gradle log instead of inside a watchdog, then run
  `python3 compat-tests/jt run "$SELECTION"` under `xvfb-run`, with `CF_API_KEY`
  injected as a real environment variable and never written to disk.
* Upload `<JT_HOME>/runs` as an artifact, always.

The `1.12.0` branch is the shape that proved the rest of it: while both its rows
were `skip`, its workflow launched nothing and ran the part of the matrix that can
still fail — `jt selftest` (the harness's own offline suite) and `jt envs` (every
env file must load). That is still all it dispatches: the file on the branch
provisions a JDK for a re-enabled row's Gradle wrapper but runs no client, so
today's two 1.12.2 rows are run from the workstation, through the harness build
`uitest/1.12.2-forge` that carries their client's Java 8 toolchain itself. The
workflow file itself lives on that branch, outside this workspace, and was not
touched here.

Two things to know before dispatching:

* **Dispatch is manual.** There is no `push` trigger on purpose — a dispatch
  downloads mods and launches real clients. Each workflow takes a `selection`
  input (defaulting to that branch's own rows) and an optional `exclude`, passed
  straight to `jt run`; a per-branch `concurrency` group serializes dispatches
  within a branch (a second dispatch queues rather than cancelling the first).
  The first dispatch on a fresh data root is slow — it warms the vanilla jars,
  assets and libraries and builds the world seed — and that is not a hang.
* **Nothing runs until the branch is pushed.** These checkouts live only locally
  today, so GitHub has no copy of the workflow file and the Actions tab does not
  list it. The submodule URL in `.gitmodules` is still the workstation's absolute
  path, which a hosted runner cannot fetch, so `submodules: recursive` fails until
  the branch is pushed with a URL the runner can reach (the fork's
  `compat-tests` repository). That is the one thing between this chapter and a
  dispatchable workflow.
* **World seeds and CI.** No row declares `world` today, so a CI job builds no
  seed: the harness stands each row's level up in-client. The mechanism is still
  there for a `gradle` row, and a seed is built by a vanilla *dedicated server*,
  so such a job would need a JDK for that env's major version (already required)
  and a writable `<JT_HOME>`: the server jar is ~52 MB per version, a seed is
  ~3.4 MB, and both are cached, so a job pays for them once if the cache is the
  runner's cache directory.
* The `1.21.1` rows reach their JEI marker **only with a level loaded**. No row
  declares `world` today: every row runs its own in-client harness, whose runner
  creates the level the scenarios are asserted against and quits the client when
  they are done (see "The in-client UI test harness" above), so the marker
  arrives because the level does. `mv-1.19.2-forge` is the row that shows why the
  level cannot come from a command-line join: quick play needs 1.20+, so before
  its own harness build it carried `skip` with the marker unreachable. Its fabric
  sibling runs the same MC version and reaches the same marker the same way, from
  the level its harness runner creates. The two `1.12.2-*` rows are **harness rows
  too**, not skipped ones: their `uitest/1.12.2-forge` build boots a 1.12.2 client
  on this machine with its own conditional arm64 LWJGL 2 natives repository, and
  stages the JEI-line jar it is handed with the access transformer remapped (see
  "Env matrix" below and [envs/README.md](envs/README.md), "The 1.12.2 rows and
  the gradle launch"). Like the other harness rows they therefore load their own
  level rather than joining one.
* **Known limitation:** a production client logs at INFO, so DEBUG-level lines
  are not in `latest.log`. The modern line's transformer hit line is exactly such
  a line (`Transformed <class> with <Transformer>`, logged at DEBUG by the
  coremod), so how many classes JECh actually patched cannot be asserted today;
  raising the log4j level for the run is a follow-up, after which those checks can
  be added.
* **Resolved limitation (was): world load.** A run used to stop at the main menu,
  and JEI runs `registerAdvancedSearch` — the phase that logs JECh's
  `Registering JEI pinyin search storage` — only once a level exists (measured
  for JEI 15, 19, 26 and 29 across the 1.20.1/1.21.1/1.21.10/26.1.2 rows). The
  level now comes from the row's own harness, whose runner creates it and pins it
  for the scenarios to run in, so the whole "JEI plugin present" family is
  assertable: every row of that family that claims the marker passes on a real
  run. The route before the standalone builds was a world seed generated by the
  vanilla server (`jt seed-world`) and handed to the client as
  `--quickPlaySingleplayer` through the row's own run task; it could not reach
  `mv-1.19.2-forge`, which needs a level quick play cannot give it before 1.20
  (re-measured 2026-10-14 on the since-removed instance launcher, whose metadata
  gains the quick-play trait only at 1.20, and a world target has no fallback
  argument), and it left `skip` when its own harness build began creating that
  level. Its fabric sibling reaches the marker the same way now, from the same
  kind of harness level. See
  [envs/README.md](envs/README.md) for the rejected launch paths and the seed
  recipe.

## Env matrix

22 envs: every (version line, loader) pair, all tagged `smoke`. Every one of them
runs its own standalone harness build under `uitest/`, which the env's `uitest`
block names. The `experimental` rows have
doubtful upstream version pins, `legacy` marks the 1.12.2 JEI row, and any env
that declares `skip` is also tagged `skip`. Per-env loader/java/target versions,
the check families and the resolution rules are in
[envs/README.md](envs/README.md); so is the state of the four newest rows
(`moderner-26.2` ×2, `moderner-26.3` ×2) — pinned, compiling and green since
2026-10-18.

| version line | mc | neoforge | fabric | forge |
| --- | --- | --- | --- | --- |
| `moderner` | 1.21.10 | `21.10.64` | `0.19.2` | — |
| `moderner` | 1.21.11 | `21.11.48` | `0.20.0` | — |
| `moderner` | 26.1.2 | `26.1.2.59-beta` | `0.19.2` | — |
| `moderner` | 26.2 | `26.2.0.93` | `0.20.0` | — |
| `moderner` | 26.3 | `26.3.0.88` | `0.20.0` | — |
| `1211` | 1.21.1 | `21.1.215` | `0.16.14` | — |
| `mv` | 1.20.1 | — | `0.16.14` (see envs README) | `47.0.35` |
| `mv` | 1.19.2 | — | `0.16.14` (see envs README) | `43.2.3` |
| `mv` | 1.18.2 | — | `0.14.9` | `40.2.1` |
| `mv` | 1.16.5 | — | `0.14.9` | `36.2.42` |
| `112` | 1.12.2 | — | — | `14.23.5.2864` (HEI and legacy JEI) |

```bash
python3 jt resolve all --exclude tag:experimental && python3 jt run all --exclude tag:experimental
```

The four `mv` forge rows were the rows with **no launch layer at all**: they were
the last rows on the instance launcher, which was removed on 2026-10-14, and the
same checkout's Forge dev runs cannot host the mod under test — a JPMS split
package kills 1.18.2/1.19.2/1.20.1 before Minecraft starts, and 1.16.5's merged
dev jar contradicts itself (`BlockMath` calls an SRG name the
`com.mojang.math.Transformation` on its own classpath does not carry). Both were
measured on 2026-10-14 while the launcher still existed, with the no-mod control
runs, so neither is a harness defect. Each row now runs on its own standalone
harness build, which loads the mod under test without a Forge dev run and creates
its own level; the version line's fabric half is unaffected. Details and the
measurements are in
[envs/README.md](envs/README.md), "Forge rows and the gradle launch".

Six rows have been parked over this matrix's history, and none of them was a
harness failure — each names a capability that was gone, not a JECh regression.
All six run today; **no env carries `skip`**:

* `mv-1.20.1-forge`, `mv-1.18.2-forge` and `mv-1.16.5-forge` — declared `skip` on
  2026-10-14, when the instance launcher was removed: their checkout's Forge dev
  runs cannot host the mod under test (see the paragraph above). Each now runs on
  its own standalone harness build, which loads the mod under test itself.
  `mv-1.16.5-forge` had been green *on the launcher*, and its published jar is
  unaffected; only its development run is broken.
* `mv-1.19.2-forge` — declared `skip` (no quick-play world
  join before 1.20; re-measured 2026-10-14), and revived when its own harness
  build created the level that join could not reach. Its fabric sibling never
  skipped: before its move it claimed no level-dependent marker, and it now reaches
  the storage marker from its own harness level like the forge row.
* `1.12.2-forge-jei-legacy` and `1.12.2-forge-hei` — declared `skip` on
  2026-10-14 and **revived the same day**. The skip's reason was the launch: the
  `1.12.0` checkout's Gradle dev run could not boot a 1.12.2 client on this arm64
  machine (LWJGL 2's macOS natives are x86_64/i386-only, and the instance
  launcher that used to substitute arm64 natives for it is retired). The harness
  build both rows run, `uitest/1.12.2-forge`, carries that substitution itself —
  a conditional (macOS + `os.arch == aarch64`) natives repository.
  Two further dev-run blockers were cleared on the way, and neither changed a
  check: JECh's coremod NPE on a command-line mod container (the harness finds
  its JSON scenarios by classpath scan instead) and the dev run's SRG-named
  access transformers, which is why the JEI-line jar is staged into the harness
  game directory by that build itself, with its rules remapped, rather than by
  `jt`.
  Measured, with the no-mod control, the native crash, the launcher's own natives
  set and the `IllegalAccessError` a raw staged jar produces:
  [envs/README.md](envs/README.md), "The 1.12.2 rows and the gradle launch".

`mv-1.16.5-forge` was the second such row before that, and its redness was the
finding: the built jar declares `forge [36.2.41,)` in its own `mods.toml` while
its checkout pinned `forge_version=36.2.39`, so FML refused the mod and dumped a
crash report. The checkout was fixed (`forge_version=36.2.42`, satisfying the
jar's own requirement) and the row re-measured on 2026-10-14 on the then-live
instance launcher: `[PASS]` in 48s with every check green. The env's
`loader.version` moved with the fix — the mismatch was never in the env, and the
fix is a checkout fact that outlives the row's `skip`, which the row has since
left in favour of its own harness build. Details in
[envs/README.md](envs/README.md).

## Requirements

* `python3` (3.9+), no third-party packages.
* The version line's own checkout (its `gradlew` wrapper and a JDK its Gradle can
  start on) — it builds the JECh jar the row tests when the checkout has none —
  each row's own harness build under `uitest/`, and `lsof`, which names the client
  process by its working directory.
* The JDKs the envs ask for (17, 21, 25) — each env names only a major
  version, and the lookup is per-platform (see the table above); the client's own
  JDK comes from the harness build's toolchain (8 on the 1.12.2 and 1.16.5
  lines). The component source sets (`engine/common`, `engine/legacy/src`) are
  shared by every row, so they must stay compilable at that lowest level (JDK 8).
  The world seeder
  uses the same lookup for its dedicated server.
* Built JECh jars for the version line under test, in their checkouts.
* `CF_API_KEY` for CurseForge resolutions (JEI, fabric-api); Modrinth needs none.
* Network for two Mojang endpoints when a world seed is built
  (`piston-meta.mojang.com`); a cached seed needs none.
