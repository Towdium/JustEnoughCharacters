# Cross-validation report — 01-jt-overview.md

- **Verification date:** 2026-10-14
- **Verifier:** KB cross-validation pass (read-only outside `final/`; no git commands run)
- **Ground truth:** `Project/JechGround/compat-tests/` (the `jt` CLI) and, where cited, `Project/JechGround/1.21.1/`
- **Anchors re-read:** 36 anchor tokens / 61 line ranges — every `File:line` and every path anchor was re-opened in the tree and its content matched against the statement it supports
- **Commands executed:** 12 (offline only — `python3` inside `compat-tests` and `grep`; no game, no Gradle)

## Commands executed (easy-verify)

| # | command | result | agrees with the topic? |
| --- | --- | --- | --- |
| 1 | `python3 jt envs` | exit 0; prints `JT_HOME` / envs dir / prism root, then one row per env (16 rows) with mods `pinned/total`, checks, tags | yes — the `jt envs` row |
| 2 | `python3 jt run nosuchenv` | exit 1, writes `report.json`/`report.txt`/`junit.xml` | yes — "zero-match → report + `EXIT_FAIL`" |
| 3 | `python3 jt resolve nosuchenv` | exit 1, "matched 0 of 16 target(s) … nothing was run" | yes — `cmd_resolve` returns 1 on an empty `_select` |
| 4 | `python3 jt seed-world nosuchenv` | exit 1, same empty-selection wording | yes — `cmd_seed_world` returns 1 on an empty `_select` |
| 5 | `python3 jt run ''` | exit 1 (empty selection is never a pass) | yes — the "never pass an empty selection" contract |
| 6 | `python3 jt verify /tmp/does-not-exist` | exit 1, `ReportError: … not a run directory and not a report.json (fail-closed)` | yes — the `jt verify` row and `ReportError.exit_code = EXIT_FAIL` |
| 7 | `python3 jt selftest -q` | `Ran 369 tests`, `OK`, exit 0 | yes — "369 tests, no network" |
| 8 | `grep -n 'never parallelises' lib/runner.py` | line 5 | yes — the topic's own `[defer]` source-side check |
| 9 | `grep -n 'never reported as a pass' lib/__main__.py` | line 133 | yes — the topic's own `[defer]` source-side check |
| 10 | `grep -n 'EXAMPLE_TAG = ' lib/__main__.py` | line 20 | yes, after the anchor fix below |
| 11 | `ls envs/*.json \| wc -l` | 16 | yes — the shipped matrix size behind the `jt envs` row |
| 12 | `ls selftest/test_*.py \| wc -l` | 12 | yes — the offline suite behind the `jt selftest` row |

## Drift found and fixed

`lib/__main__.py` and the `jt` launcher are shorter than the mined anchors assumed; five anchors were re-pointed to the lines that actually carry the fact.

| # | claim | was | now | verified content |
| --- | --- | --- | --- | --- |
| 1 | `_HERE` self-locates | `jt:15-19` | `jt:13-15` | `_HERE = os.path.dirname(os.path.abspath(__file__))` is line 13; the `sys.path` insert is 14–15 |
| 2 | `EXAMPLE_TAG="example"` drives the "schema example" note | `lib/__main__.py:22` | `lib/__main__.py:20` | `EXAMPLE_TAG = "example"` is line 20 (was a blank line); the note itself is 109–110, inside `79-110` |
| 3 | `--list` prints candidates then continues | `lib/__main__.py:175-189` | `lib/__main__.py:149-175` | `cmd_resolve` is 149–175; the `if args.list_candidates: continue` guard is 169–170 — 175–189 is `cmd_run`/`cmd_verify`/`cmd_selftest` |
| 4 | `jt selftest` is offline unittest discovery | `lib/__main__.py:232-250` | `lib/__main__.py:186-199` | `cmd_selftest` is 186–199; 232–250 is `_env_json` |
| 5 | `cmd_seed_world`/`cmd_resolve` return 1 on an empty `_select` | `lib/__main__.py:171-173, 224-228` | `lib/__main__.py:125-126, 152-153` | `if not selected: return 1` in `cmd_seed_world` (125–126) and in `cmd_resolve` (152–153) |

See-also numbering (old scheme → new): the `## See also` pointer "05 — gate, trust layers, known boundaries" → **07**; "the scenario-side grammar is 04" → **05**.

## Residual fact-level conflicts

1. **The `lib/` module list is incomplete.** `## Packages & imports` lists 17 modules; `lib/` actually holds `mojang.py`, `net.py` and `util.py` as well (and `compat-tests/README.md:317-319` names all three). The statement reads as an exhaustive list, so it is a factual (completeness) conflict, not an anchor drift. Not fixed here: for 01/02 this pass is limited to See-also numbering.
2. **A stale cross-reference in the body.** The `jt selftest` row says "see 05"; under the current numbering the offline suite is 07's topic (old 05 = verification). Left in place for the same scope reason; it is listed here so it is not lost.

## Verdict

**trustworthy with notes** — all 61 line ranges now resolve to content that supports their claim, and 12 command checks agree with the topic; two documented body-level residuals (the incomplete `lib/` list, the "see 05" cross-reference) remain for the owner to rule on.

## Addendum — 2026-10-14 (residuals ruled on and fixed)

- **Changed:** `## Packages & imports` now names all 19 `lib/` modules — `mojang`, `net`, `util` added (residual 1); the `jt selftest` row's cross-reference "see 05" is now "see 07" (residual 2).
- **Why:** the module list read as exhaustive while `lib/` held three more modules, and "05" was a leftover of the pre-renumber scheme (old 05 = verification = 07). Neither was anchor drift; both were body-level facts to rule on, and the ruling is to fix them.
- **New anchors:** `<ct>/README.md:317-319` added next to the module list (the repo README's own copy of the set). The `jt selftest` row's anchor `lib/__main__.py:186-199` is unchanged.
- **Byte budget:** to stay under 8192 the same file gave up three secondary phrases — the `os.path.abspath(__file__)` note in the entry-point bullet, `run.json`'s `(= report_mod.RUN_META)`, and "bad lines report line numbers" in the secrets bullet.

## Addendum — 2026-10-14 (Prism backend removal: full anchor re-read, eight re-pointed)

- **Changed:** every source anchor in 01 was re-read against the post-removal tree; eight were re-pointed, all of them pure line drift (no statement rewritten): (1) `lib/runner.py:225, 354, 405-406` → `lib/runner.py:210-211, 377` — the report writes are `report_mod.write_report` + `atomic_write_json(run_dir / RUN_META)` at 210-211 and the `game.log` drain at 377, while 225/354/405-406 now hold `env_exit_code` / `entry["exitCode"]` / the launcher-tail lines; (2) `lib/runner.py:216-226` (in two bullets: the aggregate one and the declared-`skip` one) → `217-232`, because the old range stopped before `exitCodeKind == "config"` → 2 (228-229) and `PASS → 0 / else 1` (230-232) — `env_exit_code` is 217-232; (3) `<ct>/README.md:317-319` → `319-323`, the README's module list runs over 319-323 and the old range carried only its first line; (4) `README.md:350-360` → `354-361`, the env-var table, whose last row `JT_LSOF` is 361; (5) the See-also pointers — `README.md` §exit codes `:335-348` → `:337-350`, §data layout `:304-315` → `:298-317`, and `envs/README.md` §Selection grammar `:580-602` → `:882-904` (the envs README had shifted so far that 580-602 now sits inside §Launching into a world (`world`)).
- **Why:** `lib/` was rewritten to drop the Prism instance backend — the `gradle` block is now the only launch layer — which changed the length of `runner.py`, `instance.py`, `envs.py`, `report.py`, `paths.py`, `__main__.py` and `checks.py` without changing the facts 01 states (checks, exit codes, selections and layout are as before). The topic's own launch-layer sentence was already post-removal, so only anchors moved.
- **Re-read:** 40 in-scope anchor tokens / 64 line ranges (`jt`, `lib/*.py`, `README.md`, `envs/README.md`), each opened in the tree; 0 fact-level conflicts. Offline commands only: `python3 jt selftest -q` (343 tests, OK), `python3 jt envs`, and per-file `nl` reads of `lib/*.py`; no git, no network, no Gradle.
- **Byte budget:** 8176 → 8171 (the runner re-pointing freed 5 bytes), still under 8192.
