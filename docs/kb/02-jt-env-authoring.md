> When-to-read: you write or edit an env file — a version line, a pin, or the checks/skip/world/gradle/uitest blocks.

## Mental model

An env file (`envs/*.json`) describes one (version line, loader) run; `lib/envs.py` is its executable schema, `envs/README.md` its prose. Validation is fail-closed and all-at-once: unknown keys are rejected at every level, all problems in one message. `pin` is the trust boundary — a pinned artifact needs no API call; everything else (preset, seed, staged jars) is derived, never authored.

## Packages & imports

- Files `<ct>/envs/*.json`; prose `<ct>/envs/README.md`; implementation `<ct>/lib/envs.py` (`parse_env`, `load_env`, `load_all`, `find_env`, `env_files`). Anchors: `E`=`lib/envs.py`, `C`=`lib/checks.py`, `M`=`lib/modmatch.py`, `O`=`lib/options.py`, `R`=`lib/resolve.py`, `I`=`lib/instance.py`, all under `<ct>` = `Project/JechGround/compat-tests/`.

## API quick reference

| Signature | Purpose | Contract |
| --- | --- | --- |
| `name` / `mc` / `tags` | identity | `name` req `^[A-Za-z0-9._-]+$`, becomes the run id; `mc` req `^\d+(\.\d+)+$`; `tags` opt (`E:324-330, 410-431`) |
| `loader.type` / `loader.version` | loader | both req; `type` ∈ `LOADER_TYPES = ("forge","neoforge","fabric","quilt")` (`E:19, 449-459`) |
| `java.version` / `jvm.*` / `timeout_sec` | JDK, Prism JVM, watchdog | `java.version` int ≥8; `jvm.max_mem_mb` ≥512, `jvm.min_mem_mb` ≥256 (ints), `jvm.args` list, `jvm.window` `[w,h]` ≥320; defaults `DEFAULT_MAX_MEM_MB=3072`, `DEFAULT_MIN_MEM_MB=1024`, `DEFAULT_WINDOW=(1280,720)`; `timeout_sec` ≥30 else error → `DEFAULT_TIMEOUT_SEC=600` (`E:57-60, 72-88, 462-497`) |
| `ready_when.value` | self-stop marker | `{"value": "<log line>"}`; once it and all `log_contains` markers appear in `latest.log`, the runner ends the process tree, else the watchdog does (`E:169-183, 500-509`) |
| `skip` | skip reason | non-empty string; derives a `skip` tag (not written back) for `--exclude tag:skip` (`E:254-258, 512-523`) |
| `world.name` | saves folder | `{"name": "<saves folder>"}`, `^[A-Za-z0-9][A-Za-z0-9 ._-]*$`, not `.`/`..`; `WorldSpec.saves_path = f"saves/{name}"` (`E:186-206, 526-543`) |
| `gradle.task` / `.project_dir` / `.daemon_java` | Gradle run in the checkout, no Prism instance | `task` req (`:neoforge:runClient`, never bare `runClient`); `project_dir` `$JT_JECH_ROOT`-relative (default `env.jech.project`, error if neither, never absolute); `daemon_java` ≥8, default ambient `JAVA_HOME`, **not** the client JDK (`E:109-138, 557-584`) |
| `uitest.selection` | scenario selection | req, non-empty, single line, no `\n\r\t\0`; **requires `env.gradle`** (the schema enforces it); grammar: 04 (`E:141-166, 351-355, 587-605`) |
| `jech.*` | the mod under test | keys `source, path, expectModId, slug, describe, pin, project, via`; `JECH_SOURCES = ("local-build","curseforge","modrinth")`; `local-build` → **relative** `path` (never absolute) + `expectModId`; `curseforge`/`modrinth` → `slug` + `describe={"version": …}`, `pin` opt; `project` = JECh-root dir; `pin`/`via` as below (`E:209-225, 643-673`) |
| `mods[]` | extra mods | each `name` (duplicate error) + `source` ∈ `MOD_SOURCES=("curseforge","modrinth")` + `slug` + `describe` (`{"version"}`) req; `pin`/`tags`/`via` opt; joins `env.mods` only then (`E:20, 676-706`) |
| `pin` | pinned file | `_PIN_ID_KEYS = {"curseforge": "fileId", "modrinth": "versionId"}`; keys `fileId, versionId, filename, downloadUrl, sha1, sha512, size`; id a non-empty int or str; `filename` practically required; `null`/absent = unresolved (`E:46, 617-640`) |
| `via` | game-dir vs dev-classpath | `MOD_VIA = ("game-dir","dev-classpath")`, `DEFAULT_VIA = "game-dir"`; independent per `jech`/`mods[]`; `dev-classpath` stages nothing (already on the Gradle classpath; a duplicate mod id would double-load); `ResolvedArtifact.staged = (via != "dev-classpath")`; pack/isolate stage only `staged` (`E:22-29, 546-554`) |
| `describe.version` | version matching | exact version, exact filename, glob, prefix or token; no unique winner → error listing candidates; empty = newest release; scoring `weight = max(1, 10 - index) * 10`, exact field > token > glob/prefix > substring (`M:1-13, 39, 23-92`) |
| `checks[].type` | checks | req ∈ `CHECK_TYPES` (seven below); `value` req for the log trio + `file_exists`, else "value is not allowed for <type>"; `name` defaults to `"<type>:<value≤40 chars>"` (duplicates error); `file_exists`/`uitest_report` paths are instance-minecraft-dir-relative; `uitest_report` alone may omit `value` (`uitest/report.json`) (`E:34-43, 228-244, 709-780`) |
| per-type rule | | SKIP: no session (log trio) or no known instance dir (`file_exists`, `uitest_report`). `log_contains`: missing pattern → FAIL; `log_not_contains`: any hit → FAIL; `log_regex`: unmatched `re.search(pattern, log, re.MULTILINE)` → FAIL; `file_exists`: `<minecraft_dir>/<value>` must exist; `no_crash_reports`: new crash report → FAIL (before the session test); `exit_code_zero`: `goal-reached`/`ready-timeout` → PASS, `handoff` → **SKIP**, `None`/no `game_exited`/`ready_reached` false → FAIL, else 0 → PASS, non-0 → FAIL; `uitest_report`: a report that is missing, unreadable or not a dict → **FAIL never SKIP** (`C:301-303`; docstring `C:14`), as are `schema≠1` and non-int counters; `failed`/`errored`≠0 or `status != "PASS"` → FAIL (`C:283-337`) |

## Minimal example

```json
{
  "name": "my-env", "mc": "1.21.1", "loader": {"type": "neoforge", "version": "<build>"}, "java": {"version": 21},
  "checks": [{"type": "exit_code_zero"}, {"type": "no_crash_reports"}]
}
```
<!-- sample: none (data-driven JSON; no sample tree) -->

## Contracts & pitfalls

- Unknown keys are rejected at every level; `_`-prefixed keys and `$schema` are silently skipped (comment slot) (`E:320-322, 398-407`).
- Fail-closed, all at once: problems are collected before `EnvSchemaError`; a non-dict raises at once (`E:3-7, 315-364`).
- `pin` is a trust point: `resolve_env` skips artifacts that already have one (no API call), else queries, writes the env file back and prints "env file updated with pinned ids"; new pins: CF `{fileId, filename, downloadUrl}` (+`sha1`,`size`), Modrinth `{versionId, filename, downloadUrl}` (+`sha1`,`sha512`,`size`) (`R:143-163, 194-197`).
- Loading: `env_files` sorts `*.json`, `load_all` each, `find_env(selector)` matches `name` or file stem (0 hits → known list, >1 → ambiguous) (`E:303-312, 367-384`).
- `jvm_args()` returns only the env's `args`: heap size goes through Prism's `MinMemAlloc`/`MaxMemAlloc`, since `-Xmx/-Xms` in `JvmArgs` makes Prism raise an undismissable modal warning (`E:72-88`).
- `checks[].tags` is schema-accepted but has no behaviour path (`CheckSpec` keeps only type/name/value/raw) (`E:720`).
- The preset holds exactly two keys — `options.PRESET = (("onboardAccessibility","true",…), ("lang","zh_cn",…))`, one `key:value` per line of `options.txt` — staged into the pack's `overrides/`, re-staged by `I.isolate` each run. `onboardAccessibility:true` removes the modal onboarding page a headless run cannot click; `lang:zh_cn` because JECh searches pinyin (`shitou` targets Chinese names; `en_us` has `Stone`); 1.12.2's `zh_CN` warns and falls back rather than failing, so one preset serves the matrix (`O:1-30, 78-93`).
- **Never** put a `key_*` line in that preset (measured 2026-10-14): with no `version:` line the *legacy* datafixer chain runs and `OptionsKeyLwjgl3Fix.makeRule` calls `Integer.parseInt` on each `key_*` value, so a modern binding `key.keyboard.f:CONTROL` throws `java.lang.NumberFormatException` (`OptionsKeyLwjgl3Fix.java:147`), and `Options.load()` leaves **every** option at its default, dropping the file (`lang` included). `version:` would suppress it (verified both ways) but varies per MC version, so it is omitted; nothing is lost (scenes press JEI's default `key.keyboard.f`+`CONTROL`) (`O:32-60`).
  [defer] source-side check: `grep -n 'OptionsKeyLwjgl3Fix.java:147' <ct>/lib/options.py`.

## See also

- 01 — running envs; 03 — the `gradle` backend's harness; 05 — scenario selection; 07 — gates, matrix, boundaries.
