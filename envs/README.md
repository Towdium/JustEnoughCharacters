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
| `exit_code_zero` | — | the launcher process exited 0 (SKIP when Prism handed off to an already-running instance, where the game's own code is unobservable) |

Two rules keep the checks honest:

* A check whose precondition is missing **SKIPs** — it is never rendered as a
  pass. `log_*` checks SKIP when no new game session log appeared; `file_exists`
  SKIPs when no instance directory was located. The runner always adds three
  `internal:` checks (`session-started`, `watchdog`, `game-exit`) that FAIL in
  exactly those cases, so a run with missing evidence fails on a named check.
* Only **INFO-level** markers are assertable. A production instance logs at INFO
  by default, so DEBUG-level lines (for example the transformer "hit" lines) are
  not in `latest.log` and must not be used in a check until the log4j level is
  raised by the harness.

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
| `mv-1.20.1-fabric` | 1.20.1 / fabric 0.14.11 | 17 | `multiple-version` | jei 15.48.0.179, fabric-api (see below) |
| `mv-1.19.2-forge` | 1.19.2 / forge 1.19.2-43.2.3 | 17 | `multiple-version` | jei 11.30.3.1056 |
| `mv-1.19.2-fabric` | 1.19.2 / fabric 0.14.11 | 17 | `multiple-version` | jei 11.30.3.1056, fabric-api 0.68.0+1.19.2 |
| `mv-1.18.2-forge` | 1.18.2 / forge 1.18.2-40.2.1 | 17 | `multiple-version` | jei 10.2.1.1005 |
| `mv-1.18.2-fabric` | 1.18.2 / fabric 0.14.9 | 17 | `multiple-version` | jei 10.2.1.1005, fabric-api 0.67.0+1.18.2 |
| `mv-1.16.5-forge` | 1.16.5 / forge 1.16.5-36.2.39 | 8 | `multiple-version` | jei 7.8.0.1009 (pinned by file id) |
| `mv-1.16.5-fabric` | 1.16.5 / fabric 0.14.9 | 8 | `multiple-version` | fabric-api 0.42.0+1.16 (smoke) |
| `1.12.2-forge-hei` | 1.12.2 / forge 1.12.2-14.23.5.2864 | 8 | `1.12.0` (srg jar) | had-enough-items 4.34.0 |
| `1.12.2-forge-jei-legacy` | 1.12.2 / forge 1.12.2-14.23.5.2864 | 8 | `1.12.0` (srg jar) | jei 4.16.1.301 |

The version strings above are not invented: they mirror each checkout's
`gradle.properties` (`neo_version`, `forge_version`, `fabric_loader_version`,
`fabric_api_version`, `jei_version`, `jei_unobfuscated_version`, `hei_version`).

Two upstream oddities are pinned as-found and flagged rather than corrected:

* `mv-1.20.1-fabric` — `1.20.1/gradle.properties` says
  `fabric_api_version=0.76.0+1.19.4`, a 1.19.4 build. No such file exists for MC
  1.20.1 on either CurseForge or Modrinth, so that row's `pin` stays `null` and
  the env is tagged `experimental`. The newest fabric-api for 1.20.1 is
  `0.92.12+1.20.1`.
* the fabric variants' `fabric.mod.json` files declare the legacy
  `"fabric": "*"` dependency instead of `fabric-api` (only the 26.1.2 variant
  names `fabric-api` explicitly); all fabric rows therefore carry fabric-api,
  taken from the `fabric_api_version` their build compiles against.

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
* **B — no JEI plugin** (mv-1.16.5 ×2, mv-1.18.2 ×2): the failure markers only —
  `log_not_contains "Could not find targets.json. JechTransformer will not be loaded."`,
  `no_crash_reports`, `exit_code_zero`.
* **C — 1.12.2** (both rows): `log_contains "Registering JEI pinyin search index"`,
  `log_contains "Transforming class mezz/jei/ingredients/IngredientFilter for JEI integration."`,
  `log_not_contains "Invalid config syntax:"`,
  `file_exists config/JustEnoughCharacters.cfg`, `no_crash_reports`,
  `exit_code_zero`; the legacy-JEI row adds
  `log_contains "Transforming constructor of mezz/jei/suffixtree/GeneralizedSuffixTree"`,
  which the Had Enough Items row must not carry (that ASM patch belongs to the
  legacy-JEI path only).

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
4. `jt envs` (does it load?), then `jt resolve <name>` (does the description pin
   to exactly one file?), then `jt run <name>`.
