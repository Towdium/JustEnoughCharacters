# JECh compat-test KB — the CLI, the env files, and the in-client UI-test harness

A dense English quick-reference for the JECh compatibility test pipeline: `jt` (`Project/JechGround/compat-tests/`), its env files, and the dev-only in-client UI-test harness in the 1.21.1 checkout that the Gradle backend drives. Its primary reader is a deepseek subagent; the shape is the benchmark-chosen **M1 mono-table** format — one topic per file, every file the same seven sections in the same order, so a reader chasing one answer jumps to the heading that holds it and skips the rest. Enter through the routing table, open exactly one file, and follow links rather than repeating facts.

## Routing table

Find the row whose trigger matches what you are about to do; the second column names the one file you need.

| Task trigger | Read |
| --- | --- |
| Driving the matrix from the CLI: selecting envs, running them, the run pipeline, report and totals, exit codes, `JT_HOME` layout, secrets | [01-jt-overview.md](01-jt-overview.md) |
| Writing or editing an env file: a version line, a pin, the `checks`/`skip`/`world`/`gradle`/`uitest` blocks, the preset, the version-description matcher | [02-jt-env-authoring.md](02-jt-env-authoring.md) |
| The in-client harness: the three sourcesets and `-PjechTest`, the run task, `RunConfig` defaults, the two-placement rule, `verifyJechUiTest`, the report, sessions, the watchdog, `/jechtest` | [03-uitest-harness.md](03-uitest-harness.md) |
| What the harness can observe: the match recorder, its two taps, the observation and input-seam mixins, the `method` labels a scenario may assert on | [04-match-recorder.md](04-match-recorder.md) |
| Writing or changing a UI-test scenario: its JSON keys, the two load channels, fail-closed registration, screen versus handler routing, name resolution | [05-scenario-authoring.md](05-scenario-authoring.md) |
| A step's `do` name, its required and optional arguments, a default, or the builder verb behind it | [06-step-vocabulary.md](06-step-vocabulary.md) |
| Whether a change is trustworthy: L1/L2/L3, the layered red path, the in-repo negative controls, `jt selftest`, the KB gate, the matrix's schema state and its known boundaries | [07-verification.md](07-verification.md) |

## Where the facts come from

Provenance lives here and in each topic's anchors, never in `_manifest.yaml`. The set was mined on 2026-10-14 from, in order of weight:

- `Project/JechGround/compat-tests/README.md` — the CLI, the run pipeline, the Gradle backend, the harness section, exits, layout, secrets.
- `Project/JechGround/compat-tests/envs/README.md` — the env-file schema, the shipped matrix, the check families.
- `Project/JechGround/1.21.1/neoforge/src/uitest/resources/uitest/scenarios/README.md` — the author-facing scenario and step vocabulary.
- `Project/JechGround/compat-tests/` and `Project/JechGround/1.21.1/` source — read first-hand for `lib/*.py`, the harness Java, `gradle/jech-uitest.gradle`, and the env JSON; the code wins wherever a document disagrees.
- `Documents/kb-conventions.md` — the M1 shape, the gates and the generation loop this set is built by.

## Rules the set lives by

- A fact has exactly one home; when a second file needs it, link across and never restate. The attribution is fixed by the resolutions sheet: the `key_*` trap and the language fixture are 02's, `-PjechTest` and the report shape are 03's, the recorder and the `method` labels it records are 04's, the failure a step produces is 07's; and the env-side selection grammar is 01's while the scenario-side grammar is 05's — the two are never merged into one.
- Tables beat prose; signatures and JSON keys are copied verbatim, generics and key spelling included.
- Every claim carries an anchor to its evidence: `File.java:line` or `file.py:line` for source-derived facts, a `doc:line` locator for document-derived ones.
- One file per topic, and no file above **8192 bytes**; all seven topics sit at or under that line.
- **No samples tree.** This is a data-driven set: it documents JSON and CLI behaviour rather than a compiled sample, so there is no `src/samples` tree and no sample-compile gate. Every topic carries the one-line marker `<!-- sample: none (data-driven JSON; no sample tree) -->`; in a run without `--samples` its markers report as unverified warnings, which is the expected state here.
- Non-goals, so a reader does not go looking: the `ScenarioBuilder` Java scenario API (1290 lines) and `Args`' per-key error wording are out of scope — only the JSON→verb mapping is in the KB; and the Forge-row dev-classpath SRG question has no first-party evidence and is deliberately absent.

## Quality gates

```
python3 /Users/vfyjxf/ModdingGround/Documents/tools/kb_check.py \
    /Users/vfyjxf/ModdingGround/Data/jech-compat/kb-work/final
```

It runs in **degraded mode** — no `--api`, no `--samples`, the way a document-input KB is gated — and enforces five rules: index completeness (each `NN-<topic>.md` is linked here and no link is dead), manifest coverage (every file `_manifest.yaml` names exists), the 8192-byte density budget, duplication (no pair of topics over 15% of its 8-word shingles), and sample markers. The gate command and the layers it sits beside — `jt selftest`, the Gradle gates, one real `jt run` — are 07's topic.

## See-also consistency

Each topic's `## See also` names its neighbours by file and by the section that answers the cross-question, so the set is a closed link graph: 01 → 02/03/07, 02 → 01/03/05/07, 03 → 01/02/04/05/07, 04 → 03/05/06/07, 05 → 02/03/04/06/07, 06 → 04/05/07, 07 → 01/02/03/05. No topic links to a file that does not exist, and none restates what the file it links to already owns.
