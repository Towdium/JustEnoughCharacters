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

python3 jt run all --exclude tag:experimental    # build, isolate, launch, judge
python3 jt run 1.12.2-forge-jei-legacy

python3 jt verify runs/<id>           # re-check a run's report.json; non-zero unless it passed
python3 jt selftest                   # the harness's own offline unit tests
```

`jt run` is strictly serial on purpose: a 16 GB machine cannot host two modded
Minecraft clients at once.

## What a run does

1. **Resolve** the env (pinning anything still unresolved) and download every jar
   into the cache; the JECh artifact is used in place from its build directory.
2. **Render the pack** into `runs/<id>/pack` — `files` empty, every jar in
   `overrides/mods/`. It is kept as the run's exact input record.
3. **Materialize the instance** — write the env's Prism instance directly:
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
4. **Isolate**: re-sync `mods/` from the pack, archive then empty `config/`,
   empty `saves/` and `logs/`. A stale instance can never influence a verdict.
5. **Launch** `prismlauncher -d <root> --launch <instance>` and follow
   `logs/latest.log`, accepting only a file newer than the run start.
6. **Watchdog** on `timeout_sec`: it kills the process tree and the run fails.
7. **Stop when ready**: an env may declare `ready_when` — a log line that means
   the game got up. Once that line *and* every `log_contains` marker are in the
   log, the runner ends the instance's process tree itself and goes straight to
   judging (`stop_reason: goal-reached`, recorded in the report), so a run does
   not have to wait for a game that will never quit on its own. If the ready line
   lands but a marker never arrives, the run is bounded by a grace period and
   then stopped too (`stop_reason: ready-timeout`), so a broken assertion becomes
   a FAIL rather than a watchdog kill.
8. **Judge**: the env's checks plus three always-present `internal:` checks, then
   write `report.json`, `report.txt` and `junit.xml`.

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
  prism/         the Prism data root jt imports into (never the user's own launcher)
  runs/<id>/     report.json, report.txt, junit.xml, run.json, game.log, launcher.log, pack/
  state.json     env -> instance mapping plus the modset hash that justified the reuse
  secrets.env    machine-local secrets, see below
```

Repository layout: `jt` is the entry point, `lib/` the implementation (paths,
envs, resolve, pack, instance, runner, checks, report, cfapi, mrapi, net, secrets,
select, modmatch, util, errors), `envs/` the test matrix
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
| `1` | a check failed, the selection matched nothing, or the report is missing/corrupt (fail-closed) |
| `2` | bad usage, a broken env file, an unresolvable dependency, a missing external tool |
| `3` | the run exceeded `timeout_sec` (the watchdog fired) |

A multi-env selection reports the highest code it saw. `SKIP` is never a pass: a
check whose precondition is absent (no new game session, no instance directory)
skips, while the `internal:` checks FAIL for exactly that reason, so a run with
missing evidence cannot come out green.

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
  `handoff` (and a half-second "run"). Nothing in jt detects that today; a CI job
  should run one env at a time and start from a clean root.
* **Known limitation:** a production instance logs at INFO, so DEBUG-level lines
  are not in `latest.log`. The modern line's transformer hit line is exactly such
  a line (`Transformed <class> with <Transformer>`, logged at DEBUG by the
  coremod), so how many classes JECh actually patched cannot be asserted today;
  raising the log4j level for the run is a follow-up, after which those checks can
  be added.
* **Known limitation:** a run stops at the main menu and loads no world, but some
  registration work only happens once a world exists. JEI 19.x runs most of its
  plugin phases — including `registerAdvancedSearch`, the phase that logs JECh's
  `Registering JEI pinyin search storage` — when a level is loaded, so the whole
  "JEI plugin present" check family (moderner ×4, 1.21.1 ×2, mv-1.20.1 ×2,
  mv-1.19.2 ×2) cannot pass on a real `jt run` today: the marker is absent from a
  65-line main-menu `latest.log` and appears at line 98 the moment a world is
  joined. The fix is a world-load path in the harness (quick-play plus a staged
  world), not a weaker assertion; see [envs/README.md](envs/README.md).

## Env matrix

16 envs: every (version line, loader) pair, all tagged `smoke`. The
`experimental` rows have doubtful upstream version pins, `legacy` marks the
1.12.2 JEI row. Per-env loader/java/target versions, the check families and the
resolution rules are in [envs/README.md](envs/README.md).

| version line | mc | neoforge | fabric | forge |
| --- | --- | --- | --- | --- |
| `moderner` | 1.21.10 | `21.10.64` | `0.19.2` | — |
| `moderner` | 26.1.2 | `26.1.2.59-beta` | `0.19.2` | — |
| `1211` | 1.21.1 | `21.1.215` | `0.16.14` | — |
| `mv` | 1.20.1 | — | `0.14.11` | `47.0.35` |
| `mv` | 1.19.2 | — | `0.14.11` | `43.2.3` |
| `mv` | 1.18.2 | — | `0.14.9` | `40.2.1` |
| `mv` | 1.16.5 | — | `0.14.9` | `36.2.39` |
| `112` | 1.12.2 | — | — | `14.23.5.2864` (HEI and legacy JEI) |

```bash
python3 jt resolve all --exclude tag:experimental && python3 jt run all --exclude tag:experimental
```

## Requirements

* `python3` (3.9+), no third-party packages.
* Prism Launcher with its CLI (`JT_PRISM_LAUNCHER` if not on `PATH`), and a
  Minecraft account configured in the Prism root jt uses.
* The JDKs the envs ask for (8, 17, 21, 25) — each env names only a major
  version, and the lookup is per-platform (see the table above).
* Built JECh jars for the version line under test, in their checkouts.
* `CF_API_KEY` for CurseForge resolutions (JEI, fabric-api); Modrinth needs none.
