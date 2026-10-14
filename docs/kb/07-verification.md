> When-to-read: before calling a change trustworthy, before running the offline suite or the KB gate, or before reading the matrix and its known boundaries as they stand.

`WS` = `/Users/vfyjxf/ModdingGround/`; `CT` = `WS/Project/JechGround/compat-tests/`; `J` = `WS/Project/JechGround/1.21.1/`; `CD` = `WS/Documents/`.

## Mental model

Trust is built at three independent levels: jt's offline suite, the checkout's Gradle gates around the harness, and one real `jt run` against a real client. L1/L2/L3 are a naming layer over check families the repo already ships — boot integrity, adaptation markers, real behaviour — not a new mechanism. One failing scenario step reddens all three layers at once, and the layers are wired, not observed by hand. Boundaries are recorded as boundaries, and the matrix's schema state is stated as it stands today, never as older prose describes it.

## Packages & imports

| Unit | Provides | Anchor |
| --- | --- | --- |
| `CT/selftest/` | the offline suite (`test_*.py`) that `jt selftest` discovers | `CT/lib/__main__.py:186-199` |

## API quick reference

L1/L2/L3 name existing checks; they are not code:

| Level | Asserts | Checks and anchor |
| --- | --- | --- |
| L1 boot integrity | it starts and does not blow up | `exit_code_zero` + `no_crash_reports`, as an env's `checks` array (`CT/envs/1.21.1-neoforge.json`; the type list is `CT/lib/envs.py:34-36`, the semantics `CT/lib/checks.py:242-282`) |
| L2 adaptation markers | the marker is present as a positive storage line, the transformer healthy as its failure line stays absent | `jei-storage-registered` = `log_contains` with the full literal `Registering JEI pinyin search storage`; `transformer-loaded` = `log_not_contains` with the full literal `Could not find targets.json. JechTransformer will not be loaded.`; on 1.12.2 the slash-named ASM rewrite marker `Transforming class mezz/jei/…` behind `jei-class-transformed` (`CT/envs/1.21.1-neoforge.json:52-54, 57-59`; `CT/envs/1.12.2-forge-jei-legacy.json`; `CT/lib/checks.py:187-221`) |
| L3 real behaviour | what a real call returned | `uitest_report` plus the step `assert_match_recorded`; a `checks[].detail` in `report.json` is the recorded call itself (`CT/lib/checks.py:283-337`; `J/neoforge/src/uitestMod/java/me/towdium/jecharacters/uitest/datadriven/ScenarioSteps.java:151-164`) |

One failing step reddens three layers, with no new machinery:

| Layer | How it turns red | Anchor |
| --- | --- | --- |
| harness | the step fails, `report.json` carries status FAIL, and `finish()` hands the client a non-zero exit — `halt(1)`, or `halt(3)` for a watchdog stall | `J/neoforge/src/uitestMod/java/me/towdium/jecharacters/uitest/engine/UITestRunner.java:670-691, 883-911` |
| checkout Gradle | `:neoforge:runClientTests` exits non-zero and `verifyJechUiTest`, wired in as `finalizedBy`, throws on a missing report or any `status != 'PASS'`, so both tasks end FAILED | `J/gradle/jech-uitest.gradle:246-295` |
| jt | the env's `uitest_report` check reads the same report and FAILs, and the non-zero client exit FAILs `exit_code_zero` too, so the run exits 1 (jt's own 0/1/2/3 contract is 01) | `CT/lib/checks.py:283-337`; `CT/lib/runner.py:78-81, 231-246` |

The offline suite: `jt selftest` runs `unittest.TestLoader().discover` over `selftest/test_*.py` (pattern `test_*.py`), prints `selftest: <ran> test(s), <problems> problem(s)[, <n> skipped]`, and returns 0/1 from `wasSuccessful()`. It touches no network — "No test touches the network: the API clients are exercised through fabricated payloads and stub http objects, and the download path is proven to reuse the cache without a request" — and held 371 tests across 12 files on 2026-10-14 (`CT/lib/__main__.py:186-199`; `CT/selftest/__init__.py:1-14`).

The KB gate: `python3 /Users/vfyjxf/ModdingGround/Documents/tools/kb_check.py <kb_dir>`, run in degraded mode (no `--api`, no `--samples`). Five rules: index completeness (every `\d{2}-[a-z-]+\.md` linked from `_index.md`, a missing index FAILing outright); manifest coverage (every file `_manifest.yaml` names exists, a missing manifest FAILing outright); density (no file over 8192 bytes); duplication (no pair over 15% shared 8-word shingles); sample markers (unverified warnings without `--samples`) (`CD/tools/kb_check.py:1-24, 32-34, 79-91, 97-123, 161-205`; `CD/kb-conventions.md:40-60, 86`).

## Minimal example

```bash
cd Project/JechGround/compat-tests
python3 jt selftest -q                          # 371 tests, offline, exit 0/1
python3 /Users/vfyjxf/ModdingGround/Documents/tools/kb_check.py \
    /Users/vfyjxf/ModdingGround/Data/jech-compat/kb-work/final
```
<!-- sample: none (data-driven JSON; no sample tree) -->

## Contracts & pitfalls

- Closed-book acceptance, the last of the generation loop's six steps: a fresh answerer reads only this KB, and where the KB describes code the answers must be written so the real toolchain compiles them (compile rate is the metric); where it describes non-code input a citing judge scores them against the fact sheet. Judge scores refine that metric, never replace it (`CD/kb-conventions.md:66-86`).
- Two negative controls already exist in-repo: the built-in `expect:false` step in `keyboard-switch.json`, which needs a `record_matches{clear:true}` before it, and `verifyHarnessIsolation`, which fails a jar still carrying harness classes, mixins or `jechtest.mixins.json` (`J/neoforge/src/uitest/resources/uitest/scenarios/keyboard-switch.json:27-32`; `J/gradle/jech-uitest.gradle:308-339`).
- Matrix schema state, measured 2026-10-14: 10 of the 16 envs launch through the Gradle backend — the `1.21.1` pair, the four `moderner` rows and the four `mv-*-fabric` rows; the test is the env JSON carrying a `gradle` block. The other 6 (`mv-*-forge` ×4, `1.12.2-*` ×2) stay instance-era — a decision, measured: the four forge modules were wired for the backend and run bare on 2026-10-14, and that checkout kills a dev run before Minecraft starts (a JPMS split package on 1.18.2/1.19.2/1.20.1: the module's own output and the jar loom injects out of `:common` both own `me.towdium.jecharacters`; a self-contradicting merged dev jar on 1.16.5). No env file can reach either (`CT/envs/README.md` §Forge rows and the gradle backend).
- `mv-1.16.5-forge`, resolved 2026-10-14. It was red on `no-crash-reports`: the built jar's `mods.toml` demanded `forge [36.2.41,)` while the checkout pinned `forge_version` 36.2.39, so FML refused the mod and dumped a crash report. Commit `a8277c1` raised the checkout to 36.2.42, the env's `loader.version` moved with it, and the row ran `[PASS]` in 48s with every check green (`Data/jech-compat/runs/mv-1.16.5-forge-20261014-145306`). It still stages the production jar into `mods/` — the fix is a checkout fact, not an env one. Two earlier attempts (08:13/08:16) never launched, dying in Prism's `AuthFlow`, hence the pre-fix docs until then (`CT/envs/README.md` §Check families).
- `mv-1.19.2-forge` is the one row left carrying a declared `skip`: JEI 11.x runs `registerAdvancedSearch` while a level loads, and the only non-interactive way to load one, `--quickPlaySingleplayer`, does not exist before 1.20. Re-measured 2026-10-14, the 1.19.2 client reached only the title screen, because Prism passes quick-play arguments only for profiles carrying `feature:is_quick_play_singleplayer`, which Prism's `net.minecraft/1.19.2.json` lacks while 1.20.1 has it. The row stays, with its checks written and `skip` naming exactly what is missing. Its fabric sibling carries no `skip`: on the Gradle backend it is a menu-level smoke row with neither `world` nor the storage-marker check. [defer: a measured conclusion] (`CT/envs/README.md:293-319`).

## See also

- 01-jt-overview.md §API quick reference — the exit-code contract the layered red ends in.
- 02-jt-env-authoring.md §API quick reference — the check types behind L1/L2/L3 and the `checks[]` schema.
- 03-uitest-harness.md §API quick reference — the harness half of the red path and of the report contract.
- 05-scenario-authoring.md §Contracts & pitfalls — the negative-control recipe an L3 assertion is built on.
