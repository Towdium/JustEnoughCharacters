# jt — the JECh compatibility test harness

`jt` launches Just Enough Characters together with a mod it must interoperate
with (JEI, or its 1.12.2-era forks) — inside a throwaway Prism Launcher instance,
or, for an env that asks for it, through the checkout's own Gradle run task — and
judges the run from evidence instead of from a human reading a log: the
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

python3 jt seed-world 1.21.1-fabric   # build (or reuse) the world an env is launched into

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
4. **Materialize the instance** (*Prism backend only*; a `gradle` env skips this
   and runs its checkout's task instead) — write the env's Prism instance
   directly:
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
   seed, and an `options.txt` the game rewrote is replaced by the preset. Only
   artifacts with `via: game-dir` are staged — see "The `gradle` backend" for the
   other value, and note the Prism backend's game directory is the instance's
   while a `gradle` run's is its own `runs/<id>/game`.
6. **Launch** `prismlauncher -d <root> --launch <instance> [--world <name>]` and
   follow `logs/latest.log`, accepting only a file newer than the run start.
   `--world` is Prism's own *join world on launch* option; the game-argument
   passthrough (`-- --quickPlaySingleplayer …`) and the patches-JSON alternative
   are both rejected, with measurements, in [envs/README.md](envs/README.md).
   A `gradle` env launches `./gradlew <task>` instead and follows the same
   `logs/latest.log` under its own game directory — see "The `gradle` backend".
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

## The `gradle` backend

An env may declare a **`gradle`** block instead of being launched as a Prism
instance. Everything outside the launch layer stays identical — resolution,
downloads, the world seed, isolation, the watchdog, `ready_when`, the log
follower and the checks — so the two backends are two answers to one question
("what process starts the client, and where does its game directory live?"), and
the report records which answer was used (`runBackend`, plus a `gradle` object
with the task and the checkout).

```json
"gradle": {
  "task": ":neoforge:runClient",
  "project_dir": "1.21.1",
  "daemon_java": 21
}
```

* **The block is the switch.** An env with it runs `./gradlew <task>` in
  `<JT_JECH_ROOT>/<project_dir>`; an env without it keeps the Prism backend.
  `project_dir` defaults to the env's `jech.project`, so the task always runs in
  the checkout the artifact under test was built from.
* **`--no-daemon`** on every invocation: a resident Gradle daemon would keep a
  several-hundred-megabyte JVM alive between runs, and the point of the backend
  is that a run's cost ends with the run.
* **`daemon_java` is the JDK that runs Gradle**, not the client's: the client's
  JDK comes from the checkout's own toolchain. Set it only when the wrapper
  cannot run on the machine's default JDK (Gradle 8.13 cannot run on Java 25);
  `null`/absent keeps the ambient `JAVA_HOME`. The lookup is the same
  `/usr/libexec/java_home` one the Prism backend uses, and the answer is verified
  against the JDK's own `release` file.
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
  the same *kind* of identity the Prism backend uses (a path only this run has,
  never a name). Reading a cwd needs `lsof` (`$JT_LSOF` to relocate it). Both
  wirings are **property-guarded**: a plain `./gradlew runClient` gets neither the
  property nor the flag and behaves exactly as before.
* **The world** travels the same way, as `-PjechQuickPlayWorld`; the checkout adds
  `--quickPlaySingleplayer <world>` to the client's program arguments. There is no
  Prism `--world` to lean on here, and the world must already be staged in the
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
* A run whose launcher is this run's own (a Gradle invocation) is always cleaned
  up: if it never produces a session, or outlives its exit grace, its process
  tree is ended. A Prism CLI in that state is still left alone — one that outlives
  the game may be draining, and ending it would turn its exit code into a signal.
* **Both backends need a live display.** The client opens a real window, so if the
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
  prism/         the Prism data root jt imports into (never the user's own launcher)
  runs/<id>/     report.json, report.txt, junit.xml, run.json, game.log, launcher.log, pack/
                 and, for a gradle run, game/ (its game directory) plus gradle.log
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
| `JT_LSOF` | the `lsof` binary, used to read a Gradle-launched client's working directory |

The lookup never trusts the helper blindly: every candidate is accepted only
after the JDK's own `release` file reports the requested major version. macOS's
`java_home` needs that, because it answers to the *release string*, not the major
number — `-v 8` matches nothing and silently returns the newest installed JDK
(`-v 1.8` is the one that finds Java 8), so the Java 8 envs query `8` and then
`1.8`. Without the check, the 1.12.2 envs would launch on JDK 25 and fail at
`VerifyJavaInstall` instead of using the Java 8 they asked for.

## CI

The workflow — `<branch>/.github/workflows/compat-tests.yml`, one per branch —
runs on the **workstation itself** through a self-hosted macOS runner
(`runs-on: [self-hosted, macOS]`): a run launches a real Minecraft client, and the
window has to be able to appear. Bootstrap checklist, in order:

1. **Register the runner against the fork** (not upstream), in a user directory
   such as `$HOME/actions-runner`:
   `./config.sh --url https://github.com/<fork>/<repo> --token <token>`, then
   install it as a **user-level launchd agent** with `./svc.sh install <user>` +
   `./svc.sh start`. That word is load-bearing: a user LaunchAgent inherits the
   logged-in GUI session, so Prism and the client it launches get a WindowServer
   session. A system-level LaunchDaemon has no GUI session, the game cannot create
   a window, and every launch fails. The runner's default labels (`self-hosted`,
   `macOS`, `ARM64`) already satisfy `runs-on: [self-hosted, macOS]`.
2. **Give the runner its own data root.** Put
   `JT_HOME_CI=/Users/vfyjxf/ModdingGround/Data/jech-compat-ci` in the runner's
   `.env` (the runner root directory, which `svc.sh`'s start script reads when the
   agent starts). The workflow reads it, fails with a named error if it is missing,
   and exports it as `JT_HOME`. The root is deliberately *not* the workstation's
   `Data/jech-compat`: **only one Prism process may own a data root at a time**, so
   a CI run must never point at the root a manual `jt run` is using. It ships
   exactly one file — `prism/accounts.json`, a real Microsoft account, mode 600,
   never committed and never printed — because Prism raises its first-run wizard
   unless an account that owns Minecraft is present (see the account bullet
   below). Everything else (`cache/`, `runs/`, `state.json`, the rest of
   `prism/`) is created on demand.
3. **Set `CF_API_KEY` as a repository secret on the fork.** Secrets do not travel
   with a fork — the upstream repository's secret is not visible there — so the
   CurseForge resolutions (JEI, fabric-api) fail without it:
   Settings → Secrets and variables → Actions → New repository secret.
4. **Dispatch by hand**: Actions → compat-tests → Run workflow. There is no `push`
   trigger on purpose, and the `selection` input defaults to the branch's own
   matrix (its description lists the default expression).

* **Nothing runs until the branch is pushed.** These checkouts live only locally
  today: GitHub has no copy of the branch, hence no copy of this workflow file, so
  the Actions tab does not even list it. Pushing the branch to the fork is the step
  that turns the checklist above into a dispatchable workflow.
* **The first run on a branch is slow — that is not a hang.** The CI root starts
  with nothing but the account, so the first run downloads what the instance needs
  (vanilla assets and libraries, the mod jars) and builds the world seed
  (~52 MB per server jar, ~3.4 MB per seed); later runs reuse the same root's
  cache. The job timeout is 90 minutes.
* **One job at a time.** A single registered runner executes one job at a time, so
  two dispatches can never share a data root; the per-branch `concurrency` group
  additionally serializes dispatches *within* a branch (a second dispatch queues
  rather than cancelling the first).
* Each per-branch fork checkout carries `compat-tests` as a **submodule**, so a
  branch's own harness and envs travel with the branch. `JT_JECH_ROOT` is the
  branch checkout root (`${{ github.workspace }}`); with `jech.project` naming the
  branch directory, resolution finds the built jar at the root level.
* **A macOS GUI session needs no virtual display.** The Linux-era `xvfb-run`
  wrapper and the mesa/llvmpipe fallback are gone: Prism opens a real window and
  the client runs on the real GL stack. Prism itself is the Homebrew CLI
  (`/opt/homebrew/bin/prismlauncher`) on the runner's `PATH`.
* **The build JDK is per branch, and the wrapper decides it.** The runner's default
  JDK is JBR 25, so only the branches whose Gradle cannot run on 25 pin
  `JAVA_HOME`: `moderner` (Gradle 9.5.0) and `1.12.0` (Gradle 9.3.1) use the
  default, `1.21.1` (Gradle 8.13) pins JDK 21, `multiple-version` (Gradle 8.1.1)
  pins JDK 17. That is the *build* JDK only — the client is launched by jt with the
  per-env JDK it resolves through `/usr/libexec/java_home`, which is also how Java 8
  is reached on macOS (the lookup queries `8`, then `1.8`, and verifies the answer
  against the JDK's own `release` file, because `-v 8` alone matches nothing and
  returns the newest installed JDK). `JT_JAVA_<N>_HOME` is therefore never needed
  here. There is no `actions/setup-java` / `actions/setup-python` step either: on a
  self-hosted runner they add nothing (python3 3.9+, the JDKs and the branch's own
  `./gradlew` are already there) and can fight the runner's environment.
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
* The `1.21.1` rows reach their JEI marker **only with the world loaded**.
  `1.21.1-fabric` declares `world` and is one of the six rows that launch
  through the checkout's Gradle task rather than Prism while still joining one
  (the other five are the `moderner` rows, moved there on 2026-10-14, and
  `mv-1.20.1-fabric`, moved there with its three siblings on the same day);
  their world travels as `-PjechQuickPlayWorld`. `1.21.1-neoforge` declares no
  world and no `ready_when`: it runs the checkout's in-client UI test harness,
  whose own runner creates the level the scenarios are asserted against and quits
  the client when they are done (see "The in-client UI test harness" above).
  `mv-1.19.2-forge` cannot join one: quick
  play needs
  1.20+, so it carries `skip` and a CI selection must decide explicitly whether to
  exclude it (`--exclude tag:skip`) or accept exit code 1 with the skip named.
  (Re-measured 2026-10-14: Prism only passes the quick-play game argument to
  versions whose profile carries `feature:is_quick_play_singleplayer`, which its
  metadata has from 1.20 on, and a world target has no fallback argument — the
  1.19.2 client reaches the title screen and nothing else. See
  [envs/README.md](envs/README.md) for the run.) Its fabric sibling runs the same
  MC version and needs no world of its own: on the gradle backend it is a
  menu-level smoke row that claims no marker (see
  [envs/README.md](envs/README.md)). The `moderner` ×4 and
  `mv-1.20.1` ×2 rows are also launched into a world and were measured the same
  way: seven envs in all stop at `goal-reached` on a real join, only
  `mv-1.19.2-forge` is skipped, and the harness row loads its own level rather
  than joining one.
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
  eight rows of that family that claim the marker pass on a real run
  (`stopReason: goal-reached`), `mv-1.19.2-forge` excepted (it carries
  `skip`), while `mv-1.19.2-fabric`, the menu-level gradle row, claims no marker
  at all. See
  [envs/README.md](envs/README.md) for the rejected launch paths and the seed
  recipe.

## Env matrix

16 envs: every (version line, loader) pair, all tagged `smoke`. Ten of them — the
`1.21.1` pair, the four `moderner` rows and the four `multiple-version` *fabric*
rows — launch through the checkout's Gradle backend rather than Prism; the other
six use Prism. The `experimental` rows have
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
| `mv` | 1.16.5 | — | `0.14.9` | `36.2.39` |
| `112` | 1.12.2 | — | — | `14.23.5.2864` (HEI and legacy JEI) |

```bash
python3 jt resolve all --exclude tag:experimental && python3 jt run all --exclude tag:experimental
```

Two rows are expected to keep the matrix red, and neither is a harness failure:

* `mv-1.19.2-forge` — declared `skip` (no quick-play world
  join before 1.20; re-measured 2026-10-14), so a selection without
  `--exclude tag:skip` exits 1 with the skip named. Its fabric sibling no longer
  skips: as a gradle row it is a menu-level smoke row with no marker to reach.
* `mv-1.16.5-forge` — FAILs on `no-crash-reports`: the built jar declares
  `forge [36.2.41,)` in its own `mods.toml` while its checkout pins
  `forge_version=36.2.39`, so FML refuses to load the mod and dumps a crash
  report. That is a real upstream mismatch this harness exists to surface; the
  env stays on the pinned Forge rather than being raised to hide it. Details in
  [envs/README.md](envs/README.md).

## Requirements

* `python3` (3.9+), no third-party packages.
* **For the Prism backend**: Prism Launcher with its CLI (`JT_PRISM_LAUNCHER` if
  not on `PATH`), and a Minecraft account configured in the Prism root jt uses.
* **For the gradle backend**: the version line's own checkout (its `gradlew`
  wrapper and a JDK its Gradle can start on — see `daemon_java`), and `lsof`.
  No Prism, no account: the client is a development run.
* The JDKs the envs ask for (8, 17, 21, 25) — each env names only a major
  version, and the lookup is per-platform (see the table above). The world seeder
  uses the same lookup for its dedicated server.
* Built JECh jars for the version line under test, in their checkouts.
* `CF_API_KEY` for CurseForge resolutions (JEI, fabric-api); Modrinth needs none.
* Network for two Mojang endpoints when a world seed is built
  (`piston-meta.mojang.com`); a cached seed needs none.
