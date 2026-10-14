# Cross-validation report — 06-step-vocabulary.md

- **Verification date:** 2026-10-14
- **Verifier:** KB cross-validation pass (read-only outside `final/`; no git commands run)
- **Ground truth:** `Project/JechGround/1.21.1/neoforge/src/uitestMod/…/uitest/datadriven/ScenarioSteps.java` and `Args.java`, plus the author-facing `src/uitest/resources/uitest/scenarios/README.md`
- **Anchors re-read:** 9 anchor tokens / 11 line ranges (every `H:` / `S:` abbreviation expanded and re-opened)
- **Commands executed:** 5 (offline only — `grep`; no game, no Gradle)

## Commands executed (easy-verify)

| # | command | result | agrees with the topic? |
| --- | --- | --- | --- |
| 1 | `grep -c 'register("' ScenarioSteps.java` | 22 | yes — "the whole 22-step vocabulary in one table" |
| 2 | `grep -n "must be between 1 and 64" ScenarioSteps.java` | line 227 (inside `224-231`) | yes — the shared `times` bound and its message |
| 3 | `grep -n 'boolean has(String key)' Args.java` | line 146 (inside `141-149`) | yes — `wait_until_screen`'s "wrote null on purpose" vs "forgot the key" |
| 4 | `grep -n 'String stringOr(\|boolean boolOr(\|int integerOr(\|List<String> stringsOrEmpty(' Args.java` | 92 / 98 / 105 / 112 | yes — the required (`string`/`integer`/`number`) vs optional (`…Or`) accessor split |
| 5 | `python3 /tmp/dump.py ScenarioSteps.java 28-60, 60-150, 145-235` (read) | all 22 `register(...)` line numbers | yes — every `(line)` in the table (44, 45, 46, 47, 49-52, 54, 56-73, 75-76, 78-84, 86-92, 94, 96, 97, 98, 100-123, 125-132, 134-149, 151-164, 166, 168-169, 171-180, 182) resolves |

## Drift found and fixed

None. All 11 ranges resolve to content supporting their claim: `H/datadriven/ScenarioSteps.java:41-183, 188-215, 224-231, 188-201, 56-63, 32`; `H/datadriven/Args.java:31-173, 141-149, 151-166`; `S/README.md:42-142`.

No See-also change was needed — 06's `## See also` already uses file links with the current numbering (04/05/07).

## Residual fact-level conflicts

None. The per-step contract verbs, required/optional split, defaults (`expect` `true`, `within_ms` `0`, `times` 1, `count` 1, `button` `"left"`) and the `args.done()` refusal all match the handlers; the "`ScenarioBuilder` and `Args` per-key wording are out of scope" non-goal matches the file's own package-private scope.

## Verdict

**trustworthy** — no drift, no conflict; 5 command checks agree, including a full re-read of the 22 `register(...)` lines.

## Addendum — 2026-10-14 (the read-time refusal's own anchor)

- **Changed:** the first bullet of `## Contracts & pitfalls` now anchors the read-time refusal at `H/datadriven/JsonScenarioReader.java:103-123` alongside the existing `H/datadriven/ScenarioSteps.java:188-201`.
- **Why:** the refusal for an unknown `do` is raised by `JsonScenarioReader.step()` while the file is read; `ScenarioSteps` owns the vocabulary list and the wording the refusal prints. The bullet stated the former and cited only the latter, so a verifier chasing "where is a bad step refused" landed on the list instead of the throw.
- **New anchors:** `JsonScenarioReader.java:103-123` (the missing `do` name at `:107`, the unknown `do` at `:110-115`, the unknown-parameter handoff at `:116-122`). Nothing was freed or spent elsewhere; the file stays well under 8192.
