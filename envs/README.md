# Env files

One JSON file per test environment, named after its `name` field (the file stem
becomes the run id). `jt envs` loads them all and `jt resolve`/`jt run` select
from them. `lib/envs.py` is the executable form of this document: it rejects
unknown keys, so a typo fails loudly instead of silently doing nothing.

Every file may carry `_`-prefixed comment keys (`_comment`, ...); `$schema` is
ignored too. Everything else must be listed below.

## The env file

```json
{
  "name": "1.21.1-neoforge",
  "tags": ["1211", "neoforge", "smoke"],
  "mc": "1.21.1",
  "loader": { "type": "neoforge", "version": "21.1.215" },
  "java": { "version": 21 },
  "jech": {
    "source": "local-build",
    "project": "1.21.1",
    "path": "neoforge/build/libs/jecharacters-1.21.1-neoforge-*.jar",
    "expectModId": "jecharacters"
  },
  "mods": [
    { "name": "jei", "source": "curseforge", "slug": "jei", "describe": { "version": "19.37.0.362" }, "pin": null }
  ],
  "checks": [
    { "type": "log_contains", "value": "Registering JEI pinyin search storage", "name": "jei-storage-registered" },
    { "type": "no_crash_reports", "name": "no-crash-reports" },
    { "type": "exit_code_zero", "name": "exit-code" }
  ],
  "ready_when": { "value": "Sound engine started" },
  "world": { "name": "jechworld" },
  "timeout_sec": 600,
  "jvm": { "max_mem_mb": 3072, "min_mem_mb": 1024, "window": [1280, 720] }
}
```

### Top level

| key | required | meaning |
| --- | --- | --- |
| `name` | yes | env id, `[A-Za-z0-9._-]+`; also the run-id prefix and the Prism instance id |
| `tags` | no | list of strings; what `tag:<t>` selections match |
| `mc` | yes | Minecraft version (`1.21.1`), used for every resolution query |
| `loader` | yes | `{type, version}`, see below |
| `java` | yes | `{version}` — the **major** version (`21`), matched against the JDKs installed on the machine |
| `jech` | yes | the mod under test, see below |
| `mods` | yes | list, may be empty |
| `checks` | yes | list, may be empty (but an env with no checks proves nothing) |
| `ready_when` | no | `{"value": "<log line>"}`, the line that marks the game as up, see below |
| `world` | no | `{"name": "<folder>"}`, the world the client is launched into, see below |
| `skip` | no | a reason string: the env is **not executed**, see below |
| `timeout_sec` | no | watchdog budget, default 600; the whole run is killed and the run fails with exit code 3 |
| `jvm` | no | `{max_mem_mb, min_mem_mb, args, window}`; defaults 3072 / 1024 / `[]` / `[1280, 720]` |

`loader.type` is one of `forge`, `neoforge`, `fabric`, `quilt`.

`loader.version` is the **mrpack dependency string** (`lib/pack.py` writes it into
`modrinth.index.json` verbatim):

| loader | the value | example |
| --- | --- | --- |
| `neoforge` | the bare NeoForge version | `21.1.215`, `21.10.64`, `26.1.2.59-beta` |
| `forge` | `<mc>-<forge version>` | `1.20.1-47.0.35`, `1.12.2-14.23.5.2864` |
| `fabric` | the **fabric-loader** version (not the fabric-api version) | `0.16.14`, `0.19.2` |
| `quilt` | the quilt-loader version | |

### `jech` — the mod under test

| key | when | meaning |
| --- | --- | --- |
| `source` | always | `local-build` (a jar in a checkout), or `curseforge` / `modrinth` (a released file) |
| `project` | `local-build`, optional | the checkout to search, see "Locating a local build" |
| `path` | `local-build`, required | glob, relative to the checkout, e.g. `neoforge/build/libs/jecharacters-*.jar`; must not be absolute |
| `expectModId` | `local-build`, required | the mod id the jar must declare (`jecharacters`); a jar that declares something else is an error, not a warning — it means the glob picked the wrong file |
| `slug` | `curseforge`/`modrinth` | the project slug (exact match, not the display name) |
| `describe.version` | `curseforge`/`modrinth` | the version description to pin, see "Version descriptions" |
| `pin` | `curseforge`/`modrinth`, optional | a resolved file; `null` makes `jt resolve` fill it in |

### `mods[]`

| key | required | meaning |
| --- | --- | --- |
| `name` | yes | label, unique within the env; it is not the file name (that comes from the pin) |
| `source` | yes | `curseforge` or `modrinth` |
| `slug` | yes | project slug |
| `describe.version` | yes | the version description to pin |
| `pin` | no | `null` until `jt resolve` fills it in |
| `tags` | no | free-form labels, for the human reading the file |

### `checks[]`

| key | required | meaning |
| --- | --- | --- |
| `type` | yes | one of the types below |
| `value` | for the types marked ✔ | a string, or a list of strings where every entry must hold |
| `name` | no | the check's name in the report; defaults to `type:value`; must be unique in the env |
| `tags` | no | ignored by the evaluator, kept for humans |

Check types:

| type | value | passes when |
| --- | --- | --- |
| `log_contains` | ✔ pattern(s) | the marker(s) appear somewhere in `latest.log` |
| `log_not_contains` | ✔ pattern(s) | none of the markers appears in `latest.log` |
| `log_regex` | ✔ regex(es) | `re.search` matches each pattern (multiline) |
| `file_exists` | ✔ relative path(s) | every path exists **under the instance's `minecraft/` directory** (e.g. `config/jecharacters.json`) |
| `no_crash_reports` | — | no new file appeared in `minecraft/crash-reports/` during the run |
| `exit_code_zero` | — | the game ended healthy: the runner deliberately stopped it once it was ready, or the game itself exited 0 (SKIP when Prism handed off to an already-running instance *and* nothing ended it deliberately) |

Three rules keep the checks honest:

* A check whose precondition is missing **SKIPs** — it is never rendered as a
  pass. `log_*` checks SKIP when no new game session log appeared; `file_exists`
  SKIPs when no instance directory was located. The runner always adds three
  `internal:` checks (`session-started`, `watchdog`, `game-exit`) that FAIL in
  exactly those cases, so a run with missing evidence fails on a named check.
* Only **INFO-level** markers are assertable. A production instance logs at INFO
  by default, so DEBUG-level lines (for example the transformer "hit" lines) are
  not in `latest.log` and must not be used in a check until the log4j level is
  raised by the harness.
* A marker that only appears **once a world is loaded** needs `world` (below).
  JEI 19.x runs most of its plugin phases — including `registerAdvancedSearch`,
  which is what logs `Registering JEI pinyin search storage` — at level load; a
  run that stops at the main menu can never satisfy it, no matter how long it
  waits. Declaring `world` moves the client into a level and the marker arrives
  a second or two after the join. Verify every new marker against a real
  `latest.log` (step 4 of "Adding an env"). Measured across the whole matrix
  (2026-10-14): JEI's level-load phase announces itself as `Sending Runtime` in
  `mezz.jei.library.load.PluginCaller` *after* `joined the game` (1.20.1: join
  at log line 105, `Sending Runtime` at 155, JECh's marker at 141), whereas a
  run that stays at the title screen shows only the startup `Sending
  ConfigManager` and never the marker — JEI 15 (1.20.1), 19 (1.21.1), 26
  (1.21.10/26.1.2) all behave this way.

### `skip` — an env that is deliberately not executed

```json
"skip": "JEI registration check needs world load; quickPlay needs 1.20+"
```

`jt run` never launches a skipped env and never renders it as a pass: the run
entry is `status: SKIP`, the report prints `[SKIP] <env> — <reason>`, and the
process exits **1**. "Not verified" is not "verified fine", so hiding a skip
takes an explicit `--exclude tag:skip`, not an inattentive reading of a green
run. (An env that declares `skip` carries a derived `skip` *tag* for that
selection; the tag is never written back into the env file.)

`skip` blocks execution only. `jt envs` still lists the env, `jt resolve` still
pins and downloads it, `jt seed-world` still maintains its world cache, and the
env keeps its `checks` as the written-down statement of what it would assert
(the reason string is what points at the missing capability).

The two `mv-1.19.2` rows are the only ones left. That skip was re-measured on
2026-10-14 rather than inherited: `mv-1.19.2-forge` was run with a real seed
staged (`saves/jechworld`, 14 files) and launched as
`prismlauncher --launch … --world jechworld`. The client reached
`Sound engine started` and sat at the title screen for three minutes — no
`Loading level`, no `Preparing spawn area`, no `joined the game` anywhere in
`latest.log` — so no level was ever loaded and the marker could not appear.
That is Prism behaving as designed, not a harness failure: Prism only appends
the quick-play game argument when the version profile carries the
`feature:is_quick_play_singleplayer` trait, and Prism's own metadata has that
trait from 1.20 on (`net.minecraft/1.19.2.json` `+traits`:
`["XR:Initial","FirstThreadOnMacOS"]`; `1.20.1`/`1.21.1` add both quick-play
traits), while a world target has no fallback argument (the server case falls
back to `--server`/`--port`; a world does not). The env was restored to `skip`
with its checks intact after the probe.

## Launching into a world (`world`)

```json
"world": { "name": "jechworld" }
```

An env with `world` is launched with Prism's own *join world on launch* option —
`prismlauncher -d <root> --launch <instance> --world <name>` — and the seed for
that world is staged as `saves/<name>/` inside the instance on every run.
`--world` is a **first-class Prism option**, not a game-argument passthrough:
Prism knows which game argument each Minecraft version wants
(`--quickPlaySingleplayer` for 1.20+) and builds it itself. The two paths that
were tried and rejected are recorded here so nobody re-treads them:

* `prismlauncher --launch <id> -- --quickPlaySingleplayer <world>`: Qt parses
  everything after `--` as a **positional url**, so the trailing arguments become
  *resource imports* to the launcher, not game arguments. Measurement: with
  `-- --quickPlaySingleplayer jechworld` the launcher opens and stays open, no
  world is joined.
* A per-instance `.minecraft/patches/*.json` version patch appending to
  `arguments.game`: workable in principle, but it only ever duplicates what
  Prism already does, and a patch file has to be kept in sync per loader. Not
  used.

Which rows declare it, as measured on 2026-10-14: `1.21.1-neoforge`,
`1.21.1-fabric`, the four `moderner` rows and the two `mv-1.20.1` rows — all ten
run to `stopReason: goal-reached` on a real join (`vfyjxf_ joined the game`,
then JECh's `Registering JEI pinyin search storage` a second later). The
`mv-1.16.5` and `mv-1.18.2` rows deliberately keep no world: their markers are
emitted at startup (and the 1.16.5/1.18.2 jars carry no JEI plugin at all), so a
world would only add a level load to what they assert. `mv-1.19.2` cannot join
one — see the `skip` section above.

One caveat that cost a full watchdog on the way (recorded so the next person
reads it as designed, not as a bug): a run whose client dies *before* the ready
line — a Fabric `Incompatible mod set!` dialog, say — has no stopping rule at
all, because the bounded grace only starts once the ready line is in the log. It
then waits out `timeout_sec` (exit 3) even though the failure is already in the
log. A ready line that lands whether or not the join succeeds is what turns a
*join* failure into a fast `ready-timeout`; a failure earlier than the ready
line is still a watchdog case.

Name restrictions: `world.name` must be a plain folder name
(`[A-Za-z0-9][A-Za-z0-9 ._-]*`, not `.`/`..`, no separators) because it becomes
one directory under `saves/` and one entry inside the pack.

The seed itself is **built by the vanilla dedicated server of the env's own
Minecraft version**, not by a client:

```bash
python3 jt seed-world 1.21.1-neoforge     # build (or verify and reuse) the seed
python3 jt seed-world 1.21.1-neoforge --refresh   # rebuild even a verifying cache
python3 jt seed-world tag:smoke --exclude tag:112
```

* the server jar comes from Mojang's `version_manifest_v2.json` → the version's
  own json → `downloads.server`, is sha1-verified and cached in
  `<JT_HOME>/cache/servers/server-<mc>.jar` (49.2 MB for 1.21.1);
* it runs in a scratch directory with `eula.txt` accepted, `level-type=flat`,
  `level-name=world` and small view/simulation distances, writes `Done (` to its
  log, then stops over its stdin;
* `world/` is copied to `<JT_HOME>/cache/worlds/<mc>-<name>/world` next to a
  `manifest.json` carrying a per-file sha256 list and a digest over it (measured
  for `1.21.1/jechworld`: 13 files, 3.32 MiB).

An env that declares no world is a no-op here ("declares no world, so there is
nothing to seed") and not a failure, so a broad selection works; but a selection
in which *nothing* needs a seed exits 1 and says so, following the same
fail-closed rule as an empty selection.

The cache key is `(mc, world.name)`, so the two 1.21.1 envs share one seed. The
manifest is what makes reuse honest: `jt seed-world` (and `jt run`, which builds
a missing seed itself) re-hashes the tree on every lookup and rebuilds when it
does not match, so a truncated or hand-edited world is never staged. The world
tree's `session.lock` is deliberately excluded from the manifest and from
staging — it is a session artefact that a client rewrites, not part of the seed.

The digest identifies the build that is on disk, not a cross-machine constant:
the world's *shape* is fixed (flat, a pinned seed), but two builds of the same
`(mc, world)` are not byte-identical — `level.dat` carries the generation
timestamps and every region chunk carries a last-update stamp. Nothing depends on
byte equality across machines; the manifest describes the tree that is actually
cached.

`jt run` builds a seed when the cache is missing, verifies it when it is there,
and re-stages it into the instance on every run (a world a previous run played
in is replaced, never merged).

## Launching without a world

An env without `world` stops at the title screen. The preset `options.txt`
(below) is still staged, so the client reaches the menu; that is the right shape
for envs whose markers are emitted during startup (the 1.12.2 rows, the 1.16.5 /
1.18.2 rows).

## The preset `options.txt`

A freshly materialized instance has no `options.txt`, and a client with no
`options.txt` shows the **"Welcome to Minecraft!" accessibility onboarding
screen before the title screen** — a modal page with a Continue button that
nothing in a headless run can press, so the run never reaches the menu. `jt`
therefore renders a minimal preset into the pack (`overrides/options.txt`, see
`lib/options.py`) and stages it into the instance on every run:

```
onboardAccessibility:true
```

One key, and only that one:

* `onboardAccessibility:true` marks the accessibility onboarding as finished.
  The client shows that screen while the value is false, which is also the state
  of a profile with no `options.txt` at all. (Field `Options.onboardAccessibility`;
  see the Minecraft Wiki's *Title Screen* and *options.txt* pages, and Mojang's
  own reproduction steps on MC-310326, which open the screen by deleting
  `options.txt`.)

Deliberately **not** preset, with the reason: `skipMultiplayerWarning` (the
warning is for joining a *server*; these runs join single-player),
`tutorialStep` (tutorial hints are toasts, not screens, and gate nothing), and
everything cosmetic — volume, resolution, render distance, GUI scale — because a
preference written down here would silently become part of what a run measures.
A key an older client does not know is ignored when it loads the file, so one
preset serves every version line.

Verified on a freshly materialized 1.21.1 instance: with the preset the client
lands on the title screen, without it the onboarding screen comes first.

## When is the game ready? (`ready_when`)

A production instance has no in-game runner: nothing in the game makes it quit,
so without a stopping rule a `jt run` can only end by the game being closed by
hand or by the watchdog (exit code 3). `ready_when` gives the run a stopping
rule.

```json
"ready_when": { "value": "Sound engine started" }
```

* **The rule.** The run is *ready* once the `ready_when` line is in
  `latest.log` **and** every `log_contains` value of the env is in it too. At
  that moment the runner ends the instance's process tree itself and judges the
  run; the report records `stopReason: "goal-reached"` and `readyReached: true`.
* **What the report tells you when it did not hold.** `readyReached` is the whole
  rule, so a `false` on its own does not say which half was missing. The report
  prints both halves separately: `readyLineSeen` (was the ready line in the log?)
  and `missingMarkers` (which `log_contains` values never arrived). On the
  1.21.1 rows the ready line lands early and the JEI marker only at world load,
  so the honest reading of a pre-`world` failure there is
  `readyLineSeen: true, missingMarkers: ['Registering JEI pinyin search storage']`
  — the marker was the missing half, not the ready line.
* **Choosing the line.** Pick a stable INFO line vanilla prints exactly once
  when the client has finished coming up (for 1.12.2 and 1.21.1 alike, the
  classic `Sound engine started` / `Setting user:` family is a good start);
  verify it against a real `latest.log` before trusting it. Order matters: since
  every `log_contains` marker must already be there, either pick a ready line
  that comes *after* your markers, or accept that the run waits for the markers.

  For an env with `world`, keep a line that the client prints **whether or not**
  the world joins (both 1.21.1 rows use `Sound engine started`, which lands ~4s
  before the join). A world-only line such as
  `Preparing start region for dimension` reads better but is a trap: if the join
  ever fails, the ready line never lands, the grace never starts, and the run
  hangs into the watchdog (exit 3) instead of failing on the marker. With a line
  that always lands, a failed join becomes a clean `ready-timeout` + FAIL.
* **A marker that never appears.** The ready line starts a bounded grace window;
  if the markers still have not shown up when it expires, the runner stops the
  run anyway and records `stopReason: "ready-timeout"`. The missing
  `log_contains` check then FAILs (exit code 1) instead of the whole run hanging
  into the watchdog — a broken assertion is a failure, not a timeout.
* **The stop settles first.** Reaching the goal does not end the session
  instantly: the runner keeps watching for `SETTLE_GRACE_SEC` (3s) before it
  kills the process tree, because a client keeps writing for a moment after the
  ready line. Measured on `mv-1.16.5-forge`: FML writes its mod-loading crash
  report (`crash-<stamp>-fml.txt`) and logs `Crash report saved to …` 0.37s
  *after* `Sound engine started`. Killing at the ready line raced that write —
  the same env PASSed once and FAILed once on identical inputs, because one run
  died before FML flushed the report the `no_crash_reports` check reads. The
  settle makes that verdict repeatable (both re-runs FAIL on the same check, with
  the crash report named in the run); it is skipped when the game has already
  exited, and it costs 3s per run.
* **`exit_code_zero` with a stopping rule.** When the runner ended the session
  itself, `exit_code_zero` PASSes whatever code the killed process reported (the
  code is an artefact of the harness). A natural exit 0 also passes; a natural
  exit before the ready line, or a non-zero one, FAILs.
* **A launcher that exits at once.** Prism's CLI hands a launch off to a Prism
  process that already owns the data root (a local socket), and then exits 0 in
  under a second with the game starting milliseconds later. The runner does not
  read that as "no session": it keeps watching for `HANDOFF_GRACE_SEC` (120s)
  for a session or a game process, and records the situation in the notes,
  naming the other launcher's pid when there is one. A genuinely failed launch
  still ends as a missing session after that grace.
* **Omitting it.** Without `ready_when` the run behaves as before: it waits for
  the game to exit on its own or for the watchdog. No shipped env omits it: a
  modded client that reaches the title screen never exits on its own, so an env
  without a stopping rule can only end in a watchdog kill (exit 3) — i.e. it is
  a broken env, not a lenient one. Every env file in `envs/` declares
  `Sound engine started`, which the client prints in 1.12.2 through 26.1.2 alike
  (verified against each client jar), and which lands *before* the world join so
  a failed join still becomes a clean `ready-timeout`.

## Pins

A pin is written by `jt resolve` and then trusted: `jt resolve` and `jt run` use a
pinned file without querying the API again, which is what makes a run
reproducible and what lets `mv-1.16.5-forge` name a known CurseForge file id
directly.

```json
"pin": {
  "fileId": 4371666,                       // curseforge: required
  "versionId": "abc123",                   // modrinth:  required
  "filename": "jei-1.16.5-7.8.0.1009.jar", // required in practice: it is the cache file name
  "downloadUrl": "https://...",
  "sha1": "cb07340f...", "sha512": "...", "size": 839446
}
```

`filename` is needed as soon as a run has to stage the jar; a pin without one is
reported as an error rather than guessed. `pin: null` (or a missing key) means
"not resolved yet".

## Locating a local build (`jech.source = local-build`)

1. The root is `$JT_JECH_ROOT` when set; otherwise it is the checkout directory
   that contains `compat-tests` (its siblings are the version lines on a
   workstation; in a CI submodule checkout it is the branch checkout itself).
2. `jech.path` is globbed in `<$JT_JECH_ROOT>/<project>` first, then in
   `<$JT_JECH_ROOT>`; the first root that matches wins. That covers both layouts:

   * workstation: `project` names a directory under one shared root
     (`project: "multiple-version"`, `path: "1.20.1/forge/build/libs/...jar"`);
   * CI: the branch checkout root already *is* what `project` names, so the
     second root matches (`project: "moderner"`,
     `path: "neoforge/build/libs/...jar"`).
3. Sources and javadoc jars are never selected. Exactly one jar must remain:
   zero matches and two-or-more matches are both errors that list what was
   searched or matched. Without `project` there is no authority to pick a
   checkout, so every subdirectory is scanned and two matches are ambiguous.

## Version descriptions

`describe.version` is matched by `lib/modmatch.py`, strictly — no winner means an
error listing the candidates, never a guess. Accepted forms:

| form | example |
| --- | --- |
| exact version | `19.37.0.362` |
| exact file name | `jei-1.21.1-neoforge-19.37.0.362.jar` |
| glob | `jei-1.21.1-*.jar` |
| token / prefix | `19.37` |

Whole-field matches beat token matches beat glob/prefix matches beat substrings;
the newest upload breaks ties. An empty description picks the newest release of
that game version + loader.

## Selection grammar

Both `jt resolve` and `jt run` take one expression, optionally subtracted by
`--exclude`. Terms are comma-separated and OR-ed:

| term | selects |
| --- | --- |
| `all`, `*` | everything |
| `<name>` | that exact env name (never a substring) |
| `tag:<tag>` | every env carrying the tag |
| `regex:<pattern>` | `re.search` over the env name |

Examples:

```bash
jt run all --exclude tag:experimental
jt resolve tag:fabric --exclude tag:112
jt run regex:'^mv-1\.1(6|8)\.2-'
jt resolve 1.21.1-neoforge,1.21.1-fabric
```

An empty or unmatched selection is never a pass: `jt run` fails with exit code 1
and says so in the report.

## The shipped matrix

16 envs, one per (version line, loader) pair. Tags: the version line
(`moderner`, `1211`, `mv`, `112`), the loader, and `smoke` on all of them;
`experimental` marks the rows whose upstream version pins are doubtful, and
`legacy` marks the 1.12.2 JEI row.

| env | mc / loader | java | jech checkout | target mods |
| --- | --- | --- | --- | --- |
| `moderner-1.21.10-neoforge` | 1.21.10 / neoforge 21.10.64 | 21 | `moderner` | jei 26.3.0.31 |
| `moderner-1.21.10-fabric` | 1.21.10 / fabric 0.19.2 | 21 | `moderner` (fabric-legacy) | jei 26.3.0.31, fabric-api 0.138.3+1.21.10 |
| `moderner-26.1.2-neoforge` | 26.1.2 / neoforge 26.1.2.59-beta | 25 | `moderner` (neoforge-unobfuscated) | jei 29.16.0.46 |
| `moderner-26.1.2-fabric` | 26.1.2 / fabric 0.19.2 | 25 | `moderner` (fabric-unobfuscated) | jei 29.16.0.46, fabric-api 0.146.1+26.1.2 |
| `1.21.1-neoforge` | 1.21.1 / neoforge 21.1.215 | 21 | `1.21.1` | jei 19.37.0.362 |
| `1.21.1-fabric` | 1.21.1 / fabric 0.16.14 | 21 | `1.21.1` | jei 19.37.0.362, fabric-api 0.116.1+1.21.1 |
| `mv-1.20.1-forge` | 1.20.1 / forge 1.20.1-47.0.35 | 17 | `multiple-version` | jei 15.48.0.179 |
| `mv-1.20.1-fabric` | 1.20.1 / fabric 0.16.14 (see below) | 17 | `multiple-version` | jei 15.48.0.179, fabric-api (see below) |
| `mv-1.19.2-forge` | 1.19.2 / forge 1.19.2-43.2.3 | 17 | `multiple-version` | jei 11.30.3.1056 — **skipped**, see below |
| `mv-1.19.2-fabric` | 1.19.2 / fabric 0.14.11 | 17 | `multiple-version` | jei 11.30.3.1056, fabric-api 0.68.0+1.19.2 — **skipped** |
| `mv-1.18.2-forge` | 1.18.2 / forge 1.18.2-40.2.1 | 17 | `multiple-version` | jei 10.2.1.1005 |
| `mv-1.18.2-fabric` | 1.18.2 / fabric 0.14.9 | 17 | `multiple-version` | jei 10.2.1.1005, fabric-api 0.67.0+1.18.2 |
| `mv-1.16.5-forge` | 1.16.5 / forge 1.16.5-36.2.39 | 8 | `multiple-version` | jei 7.8.0.1009 (pinned by file id) |
| `mv-1.16.5-fabric` | 1.16.5 / fabric 0.14.9 | 8 | `multiple-version` | fabric-api 0.42.0+1.16 (smoke) |
| `1.12.2-forge-hei` | 1.12.2 / forge 1.12.2-14.23.5.2864 | 8 | `1.12.0` (srg jar) | had-enough-items 4.34.0 |
| `1.12.2-forge-jei-legacy` | 1.12.2 / forge 1.12.2-14.23.5.2864 | 8 | `1.12.0` (srg jar) | jei 4.16.1.301 |

Every row also stages the preset `options.txt`. The ten envs that are *launched
into a world*, and the two that are *skipped*, are called out below and in the
table above:

| env | runs? | world | why |
| --- | --- | --- | --- |
| `1.21.1-neoforge`, `1.21.1-fabric` | yes | `world: {name: jechworld}` | the JEI plugin marker needs a loaded level |
| `moderner` ×4, `mv-1.20.1` ×2 | yes | `world: {name: jechworld}` | same marker, same level-load phase (measured 2026-10-14) |
| `mv-1.19.2-forge`, `mv-1.19.2-fabric` | **no** — `skip` | none | same marker, but quick play needs 1.20+ (re-measured 2026-10-14) |
| `mv-1.16.5` ×2, `mv-1.18.2` ×2, `1.12.2` ×2 | yes | none | markers are emitted during startup |

Every env also declares `ready_when: Sound engine started`; without it a client
that reaches the title screen runs until the watchdog (see "Omitting it").

The `mv-1.19.2` rows are the one family with no path to their marker: JEI 11.x's
`registerAdvancedSearch` (the phase that logs JECh's storage marker) runs at
level load, and the only non-interactive way to load a level —
`--quickPlaySingleplayer` — does not exist before 1.20. That was re-measured on
2026-10-14 rather than assumed: with a seed staged and `--world jechworld` on the
command line, the 1.19.2 client reached the title screen and never loaded a
level, because Prism only passes the quick-play argument to versions whose
profile carries the `feature:is_quick_play_singleplayer` trait (Prism's
`net.minecraft/1.19.2.json` has no such trait; 1.20.1 does). So the envs stay in
the matrix with their checks written down, and `skip` names exactly what is
missing. Removing the `skip` is a follow-up that needs one of: a 1.19.2-era way
to join a world from the command line, or an in-game runner (a test mod that
loads a world and quits). Neither is in place; a weaker assertion is not an
option.

The version strings above are not invented: they mirror each checkout's
`gradle.properties` (`neo_version`, `forge_version`, `fabric_loader_version`,
`fabric_api_version`, `jei_version`, `jei_unobfuscated_version`, `hei_version`).

The `mv-1.20.1-fabric` row corrects two upstream pins rather than pinning them
as-found, because with them as-found the modset does not load at all:

* `1.20.1/gradle.properties` says `fabric_api_version=0.76.0+1.19.4`, a 1.19.4
  build. No such file exists for MC 1.20.1 on either CurseForge or Modrinth, so
  that row pins the real 1.20.1 line `0.92.12+1.20.1` instead — the newest
  fabric-api for 1.20.1 — and stays tagged `experimental`.
* the same file's `fabric_loader_version=0.14.11` cannot load that modset: the
  pinned `jei 15.48.0.179` requires `fabricloader >=0.16.3` and
  `fabric-api 0.92.12+1.20.1` requires `>=0.16.10`, so Fabric refuses the set
  with `Incompatible mod set!` before Minecraft starts (measured: the first run
  of this row FAILed with exactly that line and no game session). The row
  therefore uses loader `0.16.14`, the version the 1.21.1 fabric row already
  runs; Prism's `net.fabricmc.fabric-loader` metadata does not bind loader
  versions to a game version, so this is a one-token change.

Separately, and true of every fabric row rather than of this one: the fabric
variants' `fabric.mod.json` files declare the legacy `"fabric": "*"` dependency
instead of `fabric-api` (only the 26.1.2 variant names `fabric-api`
explicitly), so all fabric rows carry fabric-api, taken from the
`fabric_api_version` their build compiles against.

## Check families

The check sets follow what each version line can actually prove at INFO level.
Which family a version line belongs to is decided by the *built jar*, not by
intuition: a line whose jar contains `me/towdium/jecharacters/JechJeiPlugin.class`
has a JEI plugin and can be asserted on its registration marker.

* **A — JEI plugin present** (moderner ×4, 1.21.1 ×2, mv-1.20.1 ×2, mv-1.19.2 ×2):
  `log_contains "Registering JEI pinyin search storage"`,
  `log_not_contains "Could not find targets.json. JechTransformer will not be loaded."`,
  `no_crash_reports`, `exit_code_zero`; the fabric rows add
  `log_not_contains "Can't create config file!"` and
  `file_exists config/jecharacters.json`.

  All ten rows of this family declare `world` except the two `mv-1.19.2` rows,
  which carry `skip` (no quick play before 1.20; re-measured 2026-10-14, see the
  `skip` section). The other eight were measured against a real run on
  2026-10-14 — `1.21.1` ×2, `moderner` ×4, `mv-1.20.1` ×2 all stop at
  `goal-reached` with their marker present a second after `joined the game`. So
  the level-load claim holds for JEI 15 (1.20.1), JEI 19 (1.21.1), JEI 26
  (1.21.10) and JEI 29 (26.1.2): each of them defers `registerAdvancedSearch`
  until a level exists.
* **B — no JEI plugin** (mv-1.16.5 ×2, mv-1.18.2 ×2): the failure markers only —
  `log_not_contains "Could not find targets.json. JechTransformer will not be loaded."`,
  `no_crash_reports`, `exit_code_zero`. These rows have no world: their markers
  are startup markers, and the JECh jars for 1.16.5/1.18.2 contain no
  `JechJeiPlugin.class` at all (`unzip -l <jar> | grep JechJeiPlugin` is empty).

  **`mv-1.16.5-forge` is red, and the redness is the finding** (2026-10-14, not a
  harness bug — do not "fix" it by relaxing the env): its built jar refuses to
  load on the Forge version its own checkout pins. `multiple-version/1.16.5/forge/
  src/main/resources/META-INF/mods.toml:18` hardcodes
  `versionRange = "[36.2.41,)"` for `forge`, while
  `multiple-version/1.16.5/gradle.properties:5` pins `forge_version=36.2.39`, so
  FML answers `Mod jecharacters requires forge 36.2.41 or above / Currently,
  forge is 36.2.39`, abandons mod loading and writes
  `crash-<stamp>-fml.txt`; the client still reaches the title screen with a
  broken mod state (`Cowardly refusing to send event … to a broken mod state` in
  `latest.log`, ~1s *before* the ready line). `no_crash_reports` catches it, and
  the failure mode is worth knowing about: raising the env's Forge to 36.2.41+
  would hide the mismatch rather than report it, so the env stays on the pinned
  36.2.39 until upstream's metadata agrees with its own properties. The other
  three rows of this family are green; `mv-1.16.5-fabric` has no Forge at all,
  and neither 1.18.2 jar declares any dependency block.
* **C — 1.12.2** (both rows):
  `log_contains "Transforming class mezz/jei/ingredients/IngredientFilter for JEI integration."`,
  `log_not_contains "Invalid config syntax:"`,
  `file_exists config/JustEnoughCharacters.cfg`, `no_crash_reports`,
  `exit_code_zero`; the legacy-JEI row also carries
  `log_contains "Transforming constructor of mezz/jei/suffixtree/GeneralizedSuffixTree"`,
  which the Had Enough Items row must not carry (that ASM patch belongs to the
  legacy-JEI path only), and **skips** `Registering JEI pinyin search index`.

  Why those two rows do not share that one marker: it is emitted by
  `JechJeiPlugin.registerAdvancedSearch`, i.e. through JEI's *advanced search
  API*, and that API is the Had Enough Items fork's addition — legacy
  `jei_1.12.2-4.16.1.301.jar` exposes 75 classes under `mezz/jei/api/` and
  `IAdvancedSearchRegistry` is not among them (`HadEnoughItems_1.12.2-4.34.0.jar`
  has it, along with `ISearchIndex`, `ISearchIndexFactory`,
  `ISearchIndexBuilder(Factor)`). With legacy JEI the plugin method can never be
  called; JECh integrates there through the classic ASM patch instead
  (`TransformerJei.transformLegacyJei` replaces the `GeneralizedSuffixTree`
  constructor in both `IngredientFilter.<init>` and
  `createPrefixedSearchTree`), which is exactly what the two transformer markers
  assert. Measured 2026-10-14: the legacy row ran to `goal-reached` with both
  transformer markers present and the plugin marker absent; that is the row's
  intended shape, not a JECh regression.

  Both transformer markers quote the **JVM-internal class names, with slashes**.
  That is not a style choice: the runtime logs `ClassNode.name` as ASM holds it
  (`1.12.0`: `Transformer.transform(ClassNode)` logs
  `"Transforming class " + n.name + " for JEI integration."`), while the
  transformer's `accepts(...)` receives the dotted launchwrapper name. Asserting
  the dotted spelling there would be a check that can never pass. Likewise the
  suffix-tree patch is logged as `mezz/jei/suffixtree/GeneralizedSuffixTree` —
  the 1.12.2-era JEI package, not the 1.16+ `mezz/jei/search/suffixtree` one.

## Adding an env

1. Copy the closest existing file, rename it to the new `name`.
2. Read the checkout's `gradle.properties` and copy the loader / api / jei
   versions from there instead of typing a remembered version.
3. Check which family the new line belongs to (`unzip -l <jar> | grep JechJeiPlugin`).
4. Decide whether a marker needs a loaded level. If it does, add
   `"world": {"name": "jechworld"}` and pick a `ready_when` line the client
   prints **whether or not** the join succeeds (see "When is the game ready?").
   `jt run` builds the seed itself; `jt seed-world <name>` builds it ahead of time.
5. Give the env a `ready_when` line, from a real run's `latest.log` (a first,
   exploratory `jt run` may end at the watchdog — that log is still the evidence
   to read). An env without one can only end in a watchdog kill, because nothing
   in a modded client makes it quit at the title screen.
6. If the marker is unreachable for a version-line reason, do not soften the
   check: keep the check as written, add `"skip": "<the missing capability>"`,
   and say so here. That is different from a check whose *premise* is wrong for
   the row — a marker only one loader's API can emit, say (see the legacy-JEI
   row under "Check families"): fix the check, keep what the row does prove, and
   record the evidence here.
7. `jt seed-world <name>` (when step 4 applied), then `jt envs` (does it load?),
   then `jt resolve <name>` (does the description pin to exactly one file?),
   then `jt run <name>`.
