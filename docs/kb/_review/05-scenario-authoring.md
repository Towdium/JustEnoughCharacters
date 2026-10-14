# Cross-validation report — 05-scenario-authoring.md

- **Verification date:** 2026-10-14
- **Verifier:** KB cross-validation pass (read-only outside `final/`; no git commands run)
- **Ground truth:** `Project/JechGround/1.21.1/neoforge/src/uitest/` (the scenario source set and its `README.md`) and `neoforge/src/uitestMod/…/uitest/` (`datadriven/JsonScenarioSources.java`, `datadriven/JsonScenarioReader.java`, `datadriven/JsonTargets.java`, `ScenarioRegistry.java`, `match/MatchFilter.java`, `api/ScenarioSelection.java`)
- **Anchors re-read:** 16 anchor tokens / 30 line ranges (every `H:` / `U:` / `S:` abbreviation expanded and re-opened)
- **Commands executed:** 6 (offline only — `grep`, `ls`, `python3`; no game, no Gradle)

## Commands executed (easy-verify)

| # | command | result | agrees with the topic? |
| --- | --- | --- | --- |
| 1 | `grep -n 'scenarioKeys = Set.of' JsonScenarioReader.java` | line 25 (inside `25-28`, the unknown-key guard is 35–42) | yes — the top-level key table and the `"unknown scenario key 'X'"` message |
| 2 | `grep -n 'hotFolder' datadriven/DataDrivenScenario.java` | line 65, `hotFolder = "jech-scenarios"`; `hotDirectory()` at 60 resolves it under the game directory | yes — load channel (2), "the game directory's `jech-scenarios/*.json`, re-read on every scan" |
| 3 | `grep -n 'putIfAbsent' ScenarioRegistry.java` | line 172, first-wins with a warning (174–179) | yes — "a duplicate `name` is `putIfAbsent` — first wins, with a warning" |
| 4 | `grep -n 'recordMatches\|clearRecordedMatches' ScenarioSteps.java` | 144 / 147 | yes — the `record_matches` row's verb mapping |
| 5 | `ls neoforge/src/uitest/resources/uitest/scenarios/*.json` | `json-smoke.json`, `jei-pinyin-search.json`, `keyboard-switch.json` | yes — the worked example is a shipped file |
| 6 | `python3 /tmp/dump.py …/keyboard-switch.json 1-39` (read) | `{"do":"command","text":"/jech keyboard xiaohe"}` (9), `record_matches{clear:true}` (10, 27), `expect:false` on `shitou`→石 (31) | yes — the negative-control recipe and `S/keyboard-switch.json:27-32` |

## Drift found and fixed

None. All 30 ranges resolve to content supporting their claim: `H/datadriven/JsonScenarioSources.java:25-42, 60-88, 128-139, 159-190`; `H/ScenarioRegistry.java:24-41, 79-121, 123-181`; `H/datadriven/JsonScenarioReader.java:25-28, 35-42`; `H/datadriven/JsonTargets.java:39-77, 79-148, 150-178`; `H/match/MatchFilter.java:8-49`; `H/datadriven/ScenarioSteps.java:151-164`; `H/api/ScenarioSelection.java:9-17, 28-31, 44-48, 68-91`; `S/README.md:9-20, 26-40, 71-100, 142-148, 150-173`; `S/keyboard-switch.json:27-32`.

No See-also change was needed — 05's `## See also` already uses file links with the current numbering (02/03/04/06/07).

## Residual fact-level conflicts

None. Every distinct claim spot-checked in code holds: the two load channels, `Loaded(name, label, null, reason)` fail-closed registration, screen-vs-handler routing, live bind lookup with a "did you mean …?" list, the all-`null`-filter refusal, and the scenario-side selection grammar (`all` / name / `a,b` / `group:` / `tag:` / `regex:` / `mod:`, comma separator, subtraction).

## Verdict

**trustworthy** — no drift, no conflict; 6 command checks agree with the topic.

## Addendum — 2026-10-14 (three facts added: read-time refusal, the jt hot-folder path, `/jech`)

- **Changed:** (a) fail-closed registration now enumerates "a validation failure (unknown step, unknown key)" alongside the unreadable/unparseable/non-object cases and says a read-time refusal registers a fails-on-run scenario; (b) the load-channels row gained the hot folder's location under jt — `<runs>/<id>/game/jech-scenarios/` — and the rule that a hot file's name takes no part in matching, naming a scenario only as the fallback for an unreadable file; (c) a new `## Contracts & pitfalls` bullet points at `/jech`'s own command tree.
- **Why:** (a) the enumeration stopped at I/O and JSON-parse failures, so a reader could take an unknown step or key as something other than a registered failure — the read-time refusal is exactly the path `JsonScenarioReader` raises and the registry turns into a scenario; (b) the hot folder's absolute location is only implicit through two hops of jt code, and the brief recorded a real case where a hot file was deleted before it could be discovered; (c) a scenario that types `/jech …` gave no pointer to the mod-side definition, while the harness's `/jechtest` is 03's.
- **New anchors:** `H/datadriven/JsonScenarioReader.java:103-123` (read-time refusal); `compat-tests/lib/runner.py:314-319` and `compat-tests/lib/instance.py:234-242` (the game directory a Gradle-backed run gets); `H/datadriven/DataDrivenScenario.java:51-56, 60-65` (`fallbackName`, `hotDirectory`/`hotFolder`); `Project/JechGround/1.21.1/common/src/main/java/me/towdium/jecharacters/JechCommand.java:27-54`.
- **Anchor note:** the brief cited `lib/runner.py:317`; the load-bearing line is `minecraft_dir = instance.run_game_dir(run_dir)` at `:315`, so the cited range `314-319` covers both it and the `-PjechTest` travel comment.
- **Byte budget:** the three additions pushed the file past 8192, so it gave up ~800 bytes of secondary prose in the same file — the mental model's restatements of the two tables, the `session` row's verbatim refusal text, the top-level-key message's sample text, `jump`/`sneak` and the `key.keyboard.` prefix in the name-resolution row, the `MatchFilter` parenthetical and reworded `expect:` clause, the regex-comma note, the minimal example's `key escape` step, `(1290 lines)`, and the "control proves the switch happened" tail. Every addition above and every anchor survived the cuts; no fact was moved to another topic.
