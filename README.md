# jt — the JECh compatibility test harness

`jt` launches Just Enough Characters together with a mod it must interoperate
with (JEI, or its 1.12.2-era forks) — through the checkout's own Gradle
development run task — and
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

python3 jt seed-world 1.21.1-fabric   # build (or reuse) the world an env is launched into

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
3. **Render the pack** into `runs/<id>/pack` — `files` empty, every jar in
   `overrides/mods/`, plus the preset `overrides/options.txt` and, when the env
   declares `world`, `overrides/saves/<name>/`. It is kept as the run's exact
   input record.
4. **Own the game directory** — the checkout's run task is pointed at
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
6. **Launch** `./gradlew <task> --no-daemon -PjechRunDir=<game dir>` in
   `<JT_JECH_ROOT>/<project_dir>` and follow the game directory's
   `logs/latest.log`, accepting only a file newer than the run start. The client
   is named by its **working directory** (`lsof`), because Gradle hands the
   client its arguments through an `@argfile`; a world travels as
   `-PjechQuickPlayWorld=<name>` — the full contract is in "The `gradle`
   backend".
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

An env that runs declares a **`gradle`** block: a Gradle run task in the checkout
under test is the only launch layer there is, because the Prism instance launcher
was removed on 2026-10-14 (see [envs/README.md](envs/README.md), "`gradle` — the
launch layer"), and an env that is not declared `skip` and names no run task is a
schema error rather than a run that quietly launches nothing. Everything outside
the launch layer is unchanged — resolution, downloads, the world seed, isolation,
the watchdog, `ready_when`, the log follower and the checks — and the report
records the run task and the checkout it ran in (`gradle.task`,
`gradle.projectDir`).

```json
"gradle": {
  "task": ":neoforge:runClient",
  "project_dir": "1.21.1",
  "daemon_java": 21
}
```

* **The block is the switch to a launch.** An env with it runs
  `./gradlew <task>` in
  `<JT_JECH_ROOT>/<project_dir>`; an env without it has no launch layer at all,
  so the only other honest state is a declared `skip`.
  `project_dir` defaults to the env's `jech.project`, so the task always runs in
  the checkout the artifact under test was built from.
* **`--no-daemon`** on every invocation: a resident Gradle daemon would keep a
  several-hundred-megabyte JVM alive between runs, and the point of the backend
  is that a run's cost ends with the run.
* **`daemon_java` is the JDK that runs Gradle**, not the client's: the client's
  JDK comes from the checkout's own toolchain. Set it only when the wrapper
  cannot run on the machine's default JDK (Gradle 8.13 cannot run on Java 25);
  `null`/absent keeps the ambient `JAVA_HOME`. The lookup is the same
  `/usr/libexec/java_home` one the client's per-env JDK uses, and the answer is
  verified against the JDK's own `release` file.
* **The game directory is `runs/<id>/game`**, handed to the checkout as the
  project property `-PjechRunDir`. The checkout's run configuration turns it into
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
  to relocate it). Both
  wirings are **property-guarded**: a plain `./gradlew runClient` gets neither the
  property nor the flag and behaves exactly as before.
* **The world** travels the same way, as `-PjechQuickPlayWorld`; the checkout adds
  `--quickPlaySingleplayer <world>` to the client's program arguments. There is no
  launcher-side `--world` to lean on here (the instance launcher that offered one
  was removed on 2026-10-14), and the world must already be staged in the
  game directory (isolation does that from the pack's overrides). The one row that
  runs a checkout's *UI test harness* (`1.21.1-neoforge`) passes it too, and the
  harness run ignores it on purpose: that harness's own runner creates and pins
  the level its scenarios are asserted against.
* **Mods: `via`.** Each artifact (the mod under test and each `mods[]` entry) may
  carry `"via"`, which decides whether its jar is staged into the game
  directory's `mods/`:
  * `game-dir` (**default**) — the jar is copied from the cache. Use it for
    anything the checkout does not already have on the run's classpath.
  * `dev-classpath` — nothing is staged; the artifact is still resolved, pinned
    and reported, but the checkout's own run provides it (the module's sources
    registered as the mod, or a dependency such as `localRuntime` /
    `modImplementation`). Staging it anyway would put **two copies of one mod id**
    in front of the loader.

  Both 1.21.1 rows use `dev-classpath` for the JECh artifact — the module under
  test *is* that run's source set, so its jar has no place in `mods/`. The
  NeoForge checkout also declares `localRuntime "mezz.jei:jei-…"` at exactly the
  pinned version, so its JEI is `dev-classpath` too; the Fabric checkout has no
  JEI dependency, so there JEI is the one jar staged as `game-dir` (and
  fabric-api is `dev-classpath`, since loom remaps the declared
  `modImplementation` onto the run's classpath).

  The four `moderner` rows (moved to this backend on 2026-10-14) are `dev-classpath`
  for every artifact, JECh included: each of that line's four modules already puts
  the env's own dependencies on the run's classpath — `localRuntime` JEI at
  exactly the pinned version in both NeoForge modules (`jei_version` for 1.21.10,
  `jei_unobfuscated_version` for 26.1.2), `modRuntimeOnly` JEI and
  `modImplementation` fabric-api in `fabric-legacy`, and `runtimeOnly` JEI and
  `implementation` fabric-api in `fabric-unobfuscated` — so nothing is staged into
  `mods/`. Three things differ from the 1.21.1 rows and are worth knowing before
  editing either side of the contract:
  * `daemon_java` is `null`: the moderner wrapper is Gradle 9.5 and runs on the
    machine's default JDK (JBR 25), where 1.21.1's Gradle 8.13 could not. The
    *client's* JDK is the checkout's own and is not read from the env: 21 for
    `neoforge` (its toolchain), 25 for both 26.1.2 modules (toolchain), and 25 for
    `fabric-legacy` too — that module declares no toolchain, only
    `sourceCompatibility = 21`, so its `runClient` is launched by the JDK that runs
    Gradle. Measured `runClient` javaLauncher: neoforge 21, fabric-legacy 25,
    neoforge-unobfuscated 25, fabric-unobfuscated 25.
  * both NeoForge modules *already* had a quick-play property of their own —
    `quickPlaySingleplayerWorld`, read by their client run — and a fixed
    `run/client/Dev` game directory. The harness's `jechRunDir` and
    `jechQuickPlayWorld` are added **next to** them, not instead of them: the game
    directory is overridden only when `-PjechRunDir` is present (the original
    default line stays), and a plain
    `./gradlew :neoforge:runClient -PquickPlaySingleplayerWorld=x` behaves exactly
    as before.
  * the fabric modules had no `runs` block at all; their wiring is one added
    `loom { runs { client { … } } }` that only adds arguments to loom's default
    client run. Their `me/towdium/jecharacters/targets.json` needs no build-file
    change: the line's own `dev.vfyjxf.gradle.jech` plugin already routes
    `generateTargetConfig` into `processResources` (the 1.21.1 module's plugin does
    not, which is why that one wires it by hand).

  The `gradle` task and the properties are the *contract* between the harness and
  a checkout: changing a task name or a property name in a checkout means
  changing the env that names it. The 1.21.1 fabric module additionally routes
  the jech plugin's generated transformer targets through `processResources`:
  the plugin wires them into the `jar` task only, so a development run — which
  resolves the module's own resource output — had no
  `me/towdium/jecharacters/targets.json`, and JECh logged "Could not find
  targets.json. JechTransformer will not be loaded." (the neoforge module gets the
  same file from `coremod-neoforge`, which the run's classpath already carries).

  The four `multiple-version` *fabric* rows (moved here on 2026-10-14) follow the
  same rule per artifact: JECh is `dev-classpath` in all four (the run *is* the
  module's source set), JEI is `game-dir` in the three rows that carry it (it is
  `compileOnly` in the version-level build files), and fabric-api is
  `dev-classpath` in 1.16.5, 1.18.2 and 1.19.2 (their fabric modules declare it
  `modApi`) but `game-dir` in `mv-1.20.1-fabric`, whose pinned api jar cannot be a
  Gradle mod dependency at all — the checkout's loom 1.1 rejects every MC 1.20.1
  fabric-api from 0.91.1 on, so that module holds the api `modCompileOnly` and the
  harness stages the real one. Their `daemon_java` is `17` (Gradle 8.1.1), and the
  1.16.5 module also declares two loader libraries on its dev runtime classpath;
  both live in its build file, and the root `build.gradle` adds the PinIn every
  impl module needs. See [envs/README.md](envs/README.md) for the per-row record.
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

## The in-client UI test harness (`uitest`)

An env may declare an **`uitest`** block, which runs the checkout's own in-client
UI test harness — a dev-only mod that drives the real client through scripted
scenarios and writes its own verdict — and judges the run by that verdict instead
of by the log alone. It is the strongest thing a row can assert about a *client*:
the scenarios type into JECh's search path and assert on what the engine actually
matched, with screenshots as the evidence to look at.

```json
"uitest": { "selection": "tag:smoke,tag:jei" }
```

* **The block is the switch, and Gradle is where it lands.** The selection travels
  to the checkout as the project property `-PjechTest=<selection>` (the only gate
  on that wiring: without it the checkout loads no harness mod at all), so the
  env's `gradle.task` has to be the harness's own run task — `:neoforge:runClientTests`
  in the 1.21.1 checkout, which honours `-PjechRunDir` exactly as `runClient` does.
  `uitest` without `gradle` is refused by the schema: there would be no run to pass
  it to. Every term of the selection carries its own prefix (`tag:smoke,tag:jei`),
  because a bare term is a scenario-*name* match.
* **The client ends itself**, so the row declares **no `ready_when`**: the harness's
  runner quits the game when the selection is done, and a stopping rule would end
  the session mid-scenario and leave no report. It declares **no `world`** either,
  for the reason above. Both absences are the correct shape for such a row and are
  asserted in `selftest/test_envs.py`.
* **The report is the evidence.** The harness writes `report.json`, `report.txt`,
  `junit.xml` and `screenshots/<scenario>/` into `<game dir>/uitest` before it
  quits; the `uitest_report` check reads the report and FAILs on anything that is
  not a `PASS` with zero failed and errored scenarios — including a report that is
  missing, unreadable, or of a schema this check does not know. The log checks
  stay as well: they say the mod loaded.
* **A failing scenario is red twice over.** The harness exits non-zero when a
  scenario failed, so the checkout's own `verifyJechUiTest` fails the Gradle build,
  and `uitest_report` fails on the report, naming the scenario that failed.

`envs/README.md` has the block's schema and the row that uses it; the scenario
vocabulary is documented where the scenarios live, in the checkout
(`neoforge/src/uitest/resources/uitest/scenarios/README.md`).

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
machine and no launcher: a run compiles the checkout and starts the client
through the checkout's own Gradle run task, so all it needs is a JDK, Gradle, a
display and the harness's data root. The display is `xvfb-run`'s (software GL,
`-screen 0 1920x1080x24` — GLFW asks for a real colour depth), and **no account
is needed anywhere**: a Gradle dev run authenticates as the dev player, which is
why the launcher/account half of this chapter is gone.

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
  `1.21.1` → 21 (its Gradle 8.13 cannot run on 25), `moderner` → 21 (the 1.21.10
  toolchain and the world-seed server) and then 25 (Gradle 9.5, and the 26.1.2
  client), `multiple-version` → 17 (Gradle 8.1.1 supports neither 21 nor 25).
* Cache `JT_HOME/cache`, the Gradle User Home, and — on the NeoForge branches —
  ModDevGradle's `caches/neoformruntime/intermediate_results` separately, keyed
  by the Minecraft/loader inputs rather than by the branch's sources.
* Build the modules under test through Gradle before running, so a compile error
  fails with a Gradle log instead of inside a watchdog, then run
  `python3 compat-tests/jt run "$SELECTION"` under `xvfb-run`, with `CF_API_KEY`
  injected as a real environment variable and never written to disk.
* Upload `<JT_HOME>/runs` as an artifact, always.

The `1.12.0` branch is the exception that proves the shape: both its rows are
`skip`, so its workflow launches nothing and instead runs the part of the matrix
that can still fail — `jt selftest` (the harness's own offline suite) and
`jt envs` (every env file must load, and the table carries each row's skip
reason). It keeps a JDK and a Gradle setup on purpose, so re-enabling a 1.12.2 row
stays a change to the run steps only.

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
* **World seeds and CI.** The seed is built by a vanilla *dedicated server*, so a
  CI job that runs a world-launched env needs a JDK for that env's major version
  (already required) and a writable `<JT_HOME>`: the server jar is ~52 MB per
  version, a seed is ~3.4 MB, and both are cached, so a job pays for them once if
  the cache is the runner's cache directory. `jt seed-world --exclude tag:skip`
  builds every seed the matrix needs before the game jobs start.
* The `1.21.1` rows reach their JEI marker **only with the world loaded**.
  Six rows declare `world` and join one on a real run — `1.21.1-fabric`, the four
  `moderner` rows and `mv-1.20.1-fabric` — and their world travels to the
  checkout as `-PjechQuickPlayWorld` (six and not seven: `mv-1.20.1-forge`
  declares one too, but it is skipped). `1.21.1-neoforge` declares no
  world and no `ready_when`: it runs the checkout's in-client UI test harness,
  whose own runner creates the level the scenarios are asserted against and quits
  the client when they are done (see "The in-client UI test harness" above).
  `mv-1.19.2-forge` cannot join one: quick play needs 1.20+, so it carries `skip`
  and a CI selection must decide explicitly whether to exclude it
  (`--exclude tag:skip`) or accept exit code 1 with the skip named. Its fabric
  sibling runs the same MC version and needs no world of its own: it is a
  menu-level smoke row that claims no marker (see
  [envs/README.md](envs/README.md)). The two `1.12.2-*` rows are skipped rather
  than launched at all — their checkout's dev run cannot boot a 1.12.2 client on
  this machine (see "Env matrix" below and [envs/README.md](envs/README.md), "The
  1.12.2 rows and the gradle launch") — and the harness row loads its own level
  rather than joining one.
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
  harness now generates a world seed with the vanilla server (`jt seed-world`),
  stages it as `saves/jechworld`, and hands it to the client as
  `--quickPlaySingleplayer` through the row's own run task, so the whole "JEI
  plugin present" family is assertable: every row of that family that claims the
  marker and can run passes on a real run (`stopReason: goal-reached`) —
  `mv-1.19.2-forge` is skipped (no quick play before 1.20; re-measured
  2026-10-14 on the since-removed instance launcher, whose metadata gains the
  quick-play trait only at 1.20, and a world target has no fallback argument) and
  `mv-1.19.2-fabric`, the menu-level row, claims no marker at all. See
  [envs/README.md](envs/README.md) for the rejected launch paths and the seed
  recipe.

## Env matrix

16 envs: every (version line, loader) pair, all tagged `smoke`. Ten of them — the
`1.21.1` pair, the four `moderner` rows and the four `multiple-version` *fabric*
rows — run through a checkout's Gradle run task. The other six — the four
`multiple-version` *forge* rows and the two `1.12.2` rows — are declared `skip`,
so they are never executed (the `skip` section below says why). The
`experimental` rows have
doubtful upstream version pins, `legacy` marks the 1.12.2 JEI row, and any env
that declares `skip` is also tagged `skip`. Per-env loader/java/target versions,
the check families and the resolution rules are in
[envs/README.md](envs/README.md).

| version line | mc | neoforge | fabric | forge |
| --- | --- | --- | --- | --- |
| `moderner` | 1.21.10 | `21.10.64` | `0.19.2` | — |
| `moderner` | 26.1.2 | `26.1.2.59-beta` | `0.19.2` | — |
| `1211` | 1.21.1 | `21.1.215` | `0.16.14` | — |
| `mv` | 1.20.1 | — | `0.16.14` (see envs README) | `47.0.35` |
| `mv` | 1.19.2 | — | `0.16.14` (see envs README) | `43.2.3` |
| `mv` | 1.18.2 | — | `0.14.9` | `40.2.1` |
| `mv` | 1.16.5 | — | `0.14.9` | `36.2.42` |
| `112` | 1.12.2 | — | — | `14.23.5.2864` (HEI and legacy JEI) |

```bash
python3 jt resolve all --exclude tag:experimental && python3 jt run all --exclude tag:experimental
```

The four `mv` forge rows are the rows with **no launch layer at all**: they were
the last rows on the instance launcher, which was removed on 2026-10-14, and the
same checkout's Forge dev runs cannot host the mod under test — a JPMS split
package kills 1.18.2/1.19.2/1.20.1 before Minecraft starts, and 1.16.5's merged
dev jar contradicts itself (`BlockMath` calls an SRG name the
`com.mojang.math.Transformation` on its own classpath does not carry). Both were
measured on 2026-10-14 while the launcher still existed, with the no-mod control
runs, so neither is a harness defect. The version line's fabric half runs on
Gradle and is unaffected; details and the wiring recipe are in
[envs/README.md](envs/README.md), "Forge rows and the gradle launch".

Six rows are expected to keep the matrix red, and none of them is a harness
failure — each names a capability that is gone, not a JECh regression:

* `mv-1.20.1-forge`, `mv-1.18.2-forge` and `mv-1.16.5-forge` — declared `skip` on
  2026-10-14, when the instance launcher was removed: their checkout's Forge dev
  runs cannot host the mod under test (see the paragraph above). `mv-1.16.5-forge`
  had been green *on the launcher*, and its published jar is unaffected; only its
  development run is broken.
* `mv-1.19.2-forge` — declared `skip` (no quick-play world
  join before 1.20; re-measured 2026-10-14), so a selection without
  `--exclude tag:skip` exits 1 with the skip named. Its fabric sibling no longer
  skips: it is a menu-level row with no marker to reach.
* `1.12.2-forge-jei-legacy` and `1.12.2-forge-hei` — declared `skip` on the same
  day: the `1.12.0` checkout's Gradle dev run cannot boot a 1.12.2 client on this
  arm64 machine (LWJGL 2's macOS natives are x86_64/i386-only and there is no
  arm64 substitute on the dev-run path), and the instance launcher that used to
  substitute arm64 natives for it is retired. Measured, with the no-mod control,
  the native crash and the launcher's own natives set:
  [envs/README.md](envs/README.md), "The 1.12.2 rows and the gradle launch".

`mv-1.16.5-forge` was the second such row before that, and its redness was the
finding: the built jar declares `forge [36.2.41,)` in its own `mods.toml` while
its checkout pinned `forge_version=36.2.39`, so FML refused the mod and dumped a
crash report. The checkout was fixed (`forge_version=36.2.42`, satisfying the
jar's own requirement) and the row re-measured on 2026-10-14 on the then-live
instance launcher: `[PASS]` in 48s with every check green. The env's
`loader.version` moved with the fix — the mismatch was never in the env, and the
fix is a checkout fact that outlives the row's `skip`. Details in
[envs/README.md](envs/README.md).

## Requirements

* `python3` (3.9+), no third-party packages.
* The version line's own checkout (its `gradlew` wrapper and a JDK its Gradle can
  start on — see `daemon_java`), and `lsof`, which names the client process by
  its working directory.
* The JDKs the envs ask for (8, 17, 21, 25) — each env names only a major
  version, and the lookup is per-platform (see the table above). The world seeder
  uses the same lookup for its dedicated server.
* Built JECh jars for the version line under test, in their checkouts.
* `CF_API_KEY` for CurseForge resolutions (JEI, fabric-api); Modrinth needs none.
* Network for two Mojang endpoints when a world seed is built
  (`piston-meta.mojang.com`); a cached seed needs none.
