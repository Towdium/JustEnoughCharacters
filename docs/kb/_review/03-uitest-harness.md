# Cross-validation report — 03-uitest-harness.md

- **Verification date:** 2026-10-14
- **Verifier:** KB cross-validation pass (read-only outside `final/`; no git commands run)
- **Ground truth:** `Project/JechGround/1.21.1/` (`gradle/jech-uitest.gradle`, `neoforge/build.gradle`, the `uitestMod` / `uitestModEntry` / `uitest` source sets) and `Project/JechGround/compat-tests/` for the `CT/` anchors
- **Anchors re-read:** 17 anchor tokens / 39 line ranges (every `G:` / `N:` / `H:` / `SE:` / `CT:` / `J:` abbreviation expanded and re-opened)
- **Commands executed:** 6 (offline only — `grep` and `python3`; no game, no Gradle)

## Commands executed (easy-verify)

| # | command | result | agrees with the topic? |
| --- | --- | --- | --- |
| 1 | `grep -n "apply from: rootProject.file('gradle/jech-uitest.gradle')" neoforge/build.gradle` | line 108 | yes — "applied by `N/build.gradle:108` last" |
| 2 | `grep -n 'jech.uitest.windowWidth' gradle/jech-uitest.gradle` | line 162 | yes — the `-PjechTest` → system-property mapping (`…Window` → `windowWidth`/`windowHeight`), inside `G:157-168` |
| 3 | `grep -n 'loadedMods.set' gradle/jech-uitest.gradle` | line 178, `[jechMod, jechUiTestMod]` for `clientTests`, else `[jechMod]` | yes — the run-task row (`G:173-179`) |
| 4 | `python3 -c` read `CT/envs/1.21.1-neoforge.json` | `gradle.task` is `:neoforge:runClientTests` on line 18 | yes — `CT/envs/1.21.1-neoforge.json:18` |
| 5 | `grep -n '@Mod(UiTestConstants.modId)' …/uitestModEntry/…/UiTest.java` | line 26 | yes — see note 1 |
| 6 | `grep -n 'FMLEnvironment.production' …/uitestModEntry/…/UiTestCommands.java` | line 31 (inside `30-61`) | yes — "returning under `FMLEnvironment.production`" |

## Drift found and fixed

No line-number drift: all 39 ranges resolve to content supporting their claim, including the long engine rows (`H/engine/UITestRunner.java:48-65, 69-83, 147-160, 253-284, 310-355, 372-424, 489-566, 578-648, 697-754, 863-883, 889-935, 1002-1018`), `WorldBootstrap` (`35-45, 70-105, 107-149`), `ReportWriter` (`15-46, 49-79, 126-134`), `RunConfig` (`38-66, 100-108`, all listed defaults verbatim), `InputMode` (`9-20`), the gradle file (`G:1-11, 30-41, 44-51, 64-96, 122-147, 144-148, 173-179, 185-189, 211-228, 230-290, 292-329`) and `CT/lib/checks.py:43-61, 283-337`.

No See-also change was needed — 03's `## See also` already uses file links with the current numbering.

## Residual fact-level conflicts

None. One **anchor-coverage note**:

1. The `/jechtest` row's first clause — `@Mod(UiTestConstants.modId)` — is carried by `SE/UiTest.java:26` (the annotation), not by either cited file (`SE/UiTestCommands.java:30-61` holds the command registration and the production guard; `H/engine/UITestRunner.java:178-236` holds `runInteractive`). Adding that anchor was attempted and **reverted**: it pushed the file from 8181 to 8202 bytes and failed the 8192-byte density gate (03 sits 11 bytes under the cap, so there was no room to swap bytes out). The fact itself is true and now recorded here with its line.

## Verdict

**trustworthy with notes** — every cited line range supports its claim and 6 command checks agree; the only residual is the `@Mod` clause relying on this report for its line, because the 8192-byte budget has no room for the extra anchor.

## Addendum — 2026-10-14 (the clean task changed; three rows moved with it)

- **Changed:** (a) the sentence "a **missing** `report.json` is a failure, never a skip" moved out of the `verifyHarnessIsolation` bullet and into the `verifyJechUiTest` row, next to the `GradleException` it belongs to; (b) the two-placement rule row now says the bare-form clean **lists the output directory's direct children when it runs and deletes all but `jech-scenarios`**, and that the `-PjechRunDir` form deletes `<game dir>/uitest` alone; (c) the report-shape row gained the capture-path anchors; (d) every `G:` anchor at or below the clean task was re-derived (`185-189` → `185-205`, `211-228` → `230-244`, `230-290` → `246-295` plus the new `300-306`, `292-329` → `308-339`).
- **Why:** the clean task no longer deletes the whole bare-run game directory. Deleting it took `<game dir>/jech-scenarios` — the documented hot folder, which is an **input** — with it, so a scenario dropped there was destroyed during pre-run cleanup and could never be discovered (observed: two dropped files vanished and the run registered only the jar's 4 scenarios). The bare form now enumerates and deletes everything except that folder, in a `Provider` so the listing happens at execution time; the `-PjechRunDir` form is unchanged. The missing-report rule also belonged to the task that throws on it, not to the jar-isolation gate.
- **New anchors:** `G:185-205` (clean task, both branches), `G:230-244` (preset), `G:246-295` and `G:300-306` (verify task and `tasks.configureEach` wiring), `G:308-339` (isolation); `H/api/TestContext.java:320-327, 340-343` (`screenshotPath`, `sequencePath`) and `H/capture/FrameCapturePipeline.java:15-19, 35-37, 182` (the pipeline's own `screenshots/<scenario>/` rule) for the capture path. `FrameCapturePipeline` was dropped from the out-of-scope list, since the report-shape row now cites it for the layout.
- **Byte budget:** to absorb the new sentence under 8192 the `-PjechTest` row was compressed to the mapping rule plus its two real exceptions (`…Window=WxH`, `…KeepOpen`/`…Headless`), and five secondary phrases were dropped. **Lost content, recorded here:** the compressed row no longer says that the flags travel as system properties because Minecraft's parser rejects unknown arguments — the fact is still stated verbatim in `gradle/jech-uitest.gradle:12-13` and in `H/api/RunConfig.java`, and the row's anchor `G:30-41, 157-168` covers the mechanism.
