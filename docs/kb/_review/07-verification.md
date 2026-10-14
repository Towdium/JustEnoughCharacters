# Cross-validation report — 07-verification.md

- **Verification date:** 2026-10-14
- **Verifier:** KB cross-validation pass (read-only outside `final/`; no git commands run)
- **Ground truth:** `Project/JechGround/compat-tests/` (suite, checks, env matrix), `Project/JechGround/1.21.1/` (the Gradle gates and the harness), `Project/JechGround/multiple-version/1.16.5/`, and `Documents/tools/kb_check.py` + `Documents/kb-conventions.md`
- **Anchors re-read:** 21 anchor tokens / 29 line ranges (every `CT:` / `J:` / `CD:` abbreviation expanded and re-opened)
- **Commands executed:** 9 (offline only; no game, no Gradle)

## Commands executed (easy-verify)

| # | command | result | agrees with the topic? |
| --- | --- | --- | --- |
| 1 | `python3 Documents/tools/kb_check.py <final>` | `PASS — 7 topic files, 0 failures, 0 warnings`, exit 0 | yes — the KB-gate row (degraded mode, five rules) |
| 2 | `python3 jt selftest -q` | `Ran 369 tests in 6.550s`, `OK`, exit 0 | yes — "held 369 tests" |
| 3 | `ls selftest/test_*.py \| wc -l` | 12 | yes — "369 tests across 12 files" |
| 4 | `ls envs/*.json \| wc -l` | 16 | yes — "6 of the 16 envs" |
| 5 | `python3 -c '<env json: which carry a gradle block>'` | 6 — `1.21.1-fabric`, `1.21.1-neoforge`, `moderner-1.21.10-fabric`, `moderner-1.21.10-neoforge`, `moderner-26.1.2-fabric`, `moderner-26.1.2-neoforge` | yes — the matrix-state row, name for name, with the same test ("the env JSON carrying a `gradle` block") |
| 6 | `python3 -c '<read skip of the two mv-1.19.2 envs>'` | `JEI registration check needs world load; quickPlay needs 1.20+` on both | yes — "carry a declared `skip` … `skip` naming exactly what is missing" |
| 7 | `grep -n forge_version multiple-version/1.16.5/gradle.properties` | line 5 = `forge_version=36.2.42` | yes — the "fix landed" value in the `mv-1.16.5-forge` row |
| 8 | `grep -n versionRange multiple-version/1.16.5/forge/src/main/resources/META-INF/mods.toml` | line 18 = `versionRange = "[36.2.41,)"` | yes — the historical finding's hardcoded range |
| 9 | `python3 /tmp/dump.py envs/README.md 620-630, 645-665, 715-745; README.md 530-545` (read) | row 625 still says `forge 1.16.5-36.2.39`; 721-735 still describes the pre-fix red; `README.md:534-539` still carries the pre-fix wording | yes — "`:625` (old loader value) and `:721-735` are stale … the expected-red wording also at `CT/README.md:534-539`, all pre-fix" |

## Drift found and fixed

None. All 29 ranges resolve to content supporting their claim: `CT/lib/__main__.py:186-199`; `CT/lib/envs.py:34-36`; `CT/lib/checks.py:187-221, 242-282, 283-337`; `CT/lib/runner.py:78-81, 231-246`; `CT/selftest/__init__.py:1-14`; `J/gradle/jech-uitest.gradle:230-290, 292-329`; `J/neoforge/src/uitestMod/…/engine/UITestRunner.java:670-691, 883-911`; `J/neoforge/src/uitest/resources/uitest/scenarios/keyboard-switch.json:27-32`; `CT/envs/README.md:625, 648-661, 721-735`; `CT/README.md:534-539`; `CD/tools/kb_check.py:1-24, 32-34, 79-91, 97-123, 161-205`; `CD/kb-conventions.md:40-60, 66-86, 86`.

No See-also change was needed — 07's `## See also` already uses file links with the current numbering (01/02/03/05).

## Residual fact-level conflicts

None. Two **boundaries** are recorded rather than re-verified here:

1. **Prism-profile fact.** The 1.19.2 quick-play explanation rests on Prism's `net.minecraft/1.19.2.json` lacking `feature:is_quick_play_singleplayer` and 1.20.1 having it. That file lives in the Prism data root (`$JT_HOME/prism` or the user's launcher), not in the repos, so this pass could not re-open it; the in-repo side of the same claim is fully verified (the two envs' `skip` text, `envs/README.md:648-661`, and the envs' absence of a `gradle` block).
2. **Commit id.** The `a8277c1` (2026-10-14) attribution could not be re-checked because this pass runs no git commands; the state the topic asserts (`forge_version=36.2.42` in `multiple-version/1.16.5/gradle.properties:5`, the env's `loader.version=1.16.5-36.2.42`) is confirmed, and the topic's own "re-verification not done / still instance-era" caveat remains correct.

## Verdict

**trustworthy with notes** — anchors and all nine command checks agree, including the whole matrix-state row and the `mv-1.16.5-forge` pre-fix/post-fix split; the two items above are outside-repo boundaries the topic already flags, not conflicts.

## Addendum — 2026-10-14 (L2 row rewritten; two `jech-uitest.gradle` anchors renumbered)

- **Changed:** the L2 row now names both check types with their full literal values — `jei-storage-registered` = `log_contains` + `Registering JEI pinyin search storage`, `transformer-loaded` = `log_not_contains` + `Could not find targets.json. JechTransformer will not be loaded.` — and the framing "the adaptation left a trace" became "the marker is present as a positive storage line, the transformer healthy as its failure line stays absent". The two `J/gradle/jech-uitest.gradle` anchors moved with the clean-task fix: `230-290` → `246-295` (the verify task and its wiring) and `292-329` → `308-339` (the isolation gate).
- **Why:** the old row said `transformer-loaded` "value contains `JechTransformer`" for a check the env declares as a full-sentence `log_not_contains` — the polarity a reader would take from "contains" is the opposite of what the check asserts (the negative marker means the transformer loaded). The line numbers shifted because `jechCleanUiTestReport` grew from a single `delete` to a two-branch, execution-time enumeration.
- **New anchors:** `CT/envs/1.21.1-neoforge.json:52-54, 57-59` (the storage entry and the transformer entry verbatim, type/value/name); `J/gradle/jech-uitest.gradle:246-295, 308-339`, re-derived after the edit rather than incremented.
