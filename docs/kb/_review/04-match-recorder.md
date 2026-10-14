# Cross-validation report — 04-match-recorder.md

- **Verification date:** 2026-10-14
- **Verifier:** KB cross-validation pass (read-only outside `final/`; no git commands run)
- **Ground truth:** `Project/JechGround/1.21.1/neoforge/src/uitestMod/` — `uitest/match/MatchRecorder.java`, `match/MatchFilter.java`, `mixin/uitest/*`, `uitest/input/RawInputGate.java`, `resources/jechtest.mixins.json`
- **Anchors re-read:** 16 anchor tokens / 21 line ranges (every `H:` / `MX:` / `R:` abbreviation expanded and re-opened)
- **Commands executed:** 5 (offline only — `grep` and `python3`; no game, no Gradle)

## Commands executed (easy-verify)

| # | command | result | agrees with the topic? |
| --- | --- | --- | --- |
| 1 | `grep -c '@Inject(' MatchMixin.java` | 9 | yes — "one injection per method", matching the topic's 9-row injection table |
| 2 | `grep -n 'capacity = \|maxRememberedValues = \|maxText = \|maxDescribed = ' MatchRecorder.java` | `4_096` (64), `200_000` (66), `512` (68), `8` (70) | yes — all four caps, after the identifier fix below |
| 3 | `grep -n 'recordSearch' MatchRecorder.java` | line 147 (declaration), inside `147-163` | yes, after the anchor fix below |
| 4 | `python3 -c '<read jechtest.mixins.json>'` | 12 `client` entries, `mixins` empty, `defaultRequire 1` | yes — the config row (`R/jechtest.mixins.json:1-25`): two taps + six input-seam mixins + four accessors |
| 5 | `python3 /tmp/dump.py …/JechSearchStorageMixin.java 1-68` (read) | `put` → `rememberKey` at 50–53; `@Redirect` on `TreeSearcher.search(String)` at 56–67 | yes — the JEI-side tap row and the `@Mixin`-into-PinIn negative result at `35-41` |

## Drift found and fixed

| # | claim | was | now | verified content |
| --- | --- | --- | --- | --- |
| 1 | `recordSearch(token, results)` — candidate keys, `"<n> result(s)"` fallback, `searchMethod` label | `H/match/MatchRecorder.java:226-234` | `H/match/MatchRecorder.java:147-163` | `recordSearch` is declared at 147 and ends at 163 (`226-234` is `stats()`; the first table row's use of `226-234` is correct and was left alone) |
| 2 | recorder caps, copied verbatim | `maxDescribe=8` | `maxDescribed=8` | the field is `private static final int maxDescribed = 8;` (line 70) |

No See-also change was needed — 04's `## See also` already uses file links with the current numbering.

## Residual fact-level conflicts

None. The measured negative result ("PinIn cannot be mixin'd") stays flagged `[defer: re-run to reproduce]` in the file, which is correct: re-running it needs a client boot, which this pass does not do. Its in-repo evidence (`MX/JechSearchStorageMixin.java:35-41`, `H/match/MatchRecorder.java:28-29`) was re-read and supports the wording.

## Verdict

**trustworthy** — the two drift items are fixed and every remaining range supports its claim; 5 command checks agree.
