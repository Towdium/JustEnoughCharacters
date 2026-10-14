# Cross-validation report — 02-jt-env-authoring.md

- **Verification date:** 2026-10-14
- **Verifier:** KB cross-validation pass (read-only outside `final/`; no git commands run)
- **Ground truth:** `Project/JechGround/compat-tests/` (`lib/envs.py`, `lib/checks.py`, `lib/modmatch.py`, `lib/options.py`, `lib/resolve.py`, `lib/instance.py`) and the `envs/*.json` files
- **Anchors re-read:** 21 anchor tokens / 43 line ranges — every `E:` / `C:` / `M:` / `O:` / `R:` / `I:` abbreviation was expanded to its declared file and re-opened
- **Commands executed:** 7 (offline only — `grep` and `python3`; no game, no Gradle, no network)

## Commands executed (easy-verify)

| # | command | result | agrees with the topic? |
| --- | --- | --- | --- |
| 1 | `grep -n 'OptionsKeyLwjgl3Fix.java:147' lib/options.py` | line 44 | yes — the topic's own `[defer]` source-side check; the recorded `NumberFormatException` trace is reproduced verbatim there |
| 2 | `grep -n '^PRESET' lib/options.py` | line 81 | yes — `PRESET` holds exactly the two keys (`onboardAccessibility`, `lang`) the topic names (`O:78-93`) |
| 3 | `grep -n 'CHECK_TYPES' lib/envs.py` | line 34, seven members | yes — "`CHECK_TYPES` (seven below)" |
| 4 | `grep -n '_PIN_ID_KEYS' lib/envs.py` | line 46, `{"curseforge": "fileId", "modrinth": "versionId"}` | yes — the `pin` row (`E:46`) |
| 5 | `grep -n '^API_KEY_ENV' lib/cfapi.py` | line 19, `CF_API_KEY` | yes — `cfapi.py:19` |
| 6 | `grep -n 'weight = max' lib/modmatch.py` | line 39, `weight = max(1, 10 - index) * 10` | see note 1 below |
| 7 | `python3 -c '<list every env json's loader + gradle + skip>'` | 16 envs, loader/version values match the shipped files | yes — the topic's per-env schema claims hold on real files |

## Drift found and fixed

No line-number drift: every `E:`/`C:`/`M:`/`O:`/`R:`/`I:` anchor still resolves to content that supports its claim (spot-verified in full: `E:19, 20, 22-29, 34-43, 46, 54-56, 57-60, 72-88, 109-138, 141-166, 169-183, 186-206, 209-225, 228-244, 254-258, 303-312, 315-364, 320-322, 324-330, 351-355, 367-384, 398-407, 410-431, 449-459, 462-497, 500-509, 512-523, 526-543, 546-554, 557-584, 587-605, 617-640, 643-673, 676-706, 709-780`; `C:187-337`; `M:1-13, 23-92`; `O:1-30, 32-60, 78-93`; `R:143-163, 194-197, 322-382, 390-412`; `I:...`; plus `OptionsKeyLwjgl3Fix.java:147`).

See-also numbering (old scheme → new): "04 — scenario selection" → **05**; "05 — gates, matrix, boundaries" → **07**.

## Residual fact-level conflicts

None that change a reader's decision. Two notes:

1. **Minor imprecision, not drift.** `M:23-92` states the scoring rule as `weight=(10-index)*10`; `lib/modmatch.py:39` is `max(1, 10 - index) * 10` (clamped for `index >= 9`). For every field list a real candidate has, the two agree. Not fixed (01/02 body edits in this pass are limited to See-also numbering).
2. **Externally anchored fact.** `OptionsKeyLwjgl3Fix.java:147` points at Minecraft's own datafixer, not at this repo. Its evidence in-repo is `lib/options.py:32-60` + the verbatim trace at `:44`; re-running the throw needs the MC source/datafixer (SourceScout/Gradle), which this pass deliberately did not start. Recorded as a boundary, not a conflict.

## Verdict

**trustworthy** — all anchors resolve and 7 command checks agree; the two notes above are documented precision/boundary matters, not wrong facts.

## Addendum — 2026-10-14 (note 1 fixed, and the `uitest_report` missing-report half)

- **Changed:** the per-type rule's `uitest_report` half now leads with the missing case — "a report that is missing, unreadable or not a dict → **FAIL never SKIP**" — before the `schema≠1`/non-int-counter failures it already named. The `describe.version` scoring is now quoted verbatim as `weight = max(1, 10 - index) * 10`, closing residual note 1.
- **Why:** an env author reading the old row saw only malformed/schema/counter failures, which hides the case the check exists for — the harness never reached its runner and wrote no report at all. Note 1's clamp matters to a reader who reasons about `index >= 9`, so it is no longer paraphrased.
- **New anchors:** `C:301-303` (`if not path.is_file(): … FAIL`) and docstring `C:14` ("A missing, unreadable or unparseable report is a FAIL — never a SKIP") for the missing case, with `C:283-337` kept for the rest; `lib/modmatch.py:39` added to the row's `M:1-13, 23-92`.
- **Byte budget:** four secondary phrases were dropped to stay under 8192 — the `jei-1.21.1-*.jar` glob example, `(= min(1024, max_mem))`, "itself" in the self-stop marker row, and "(usually empty)" in the `jvm_args()` bullet.
