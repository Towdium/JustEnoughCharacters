> When-to-read: you touch the in-client UI test harness — `gradle/jech-uitest.gradle`, its sourcesets, the run task an env's `gradle.task` names — or need where a run's report lands, how it is gated and how it stops. Observation mixins and the match recorder are 04.

## Mental model

A dev-only mod (`jechtest`) in the 1.21.1 checkout over three sourcesets, gated end to end by `-PjechTest`: only `:neoforge:runClientTests` loads it, and a task proves the release jar never carries it. The render frame is the clock — one phase transition and one step per frame, no sleep. `report.json` is the whole contract: scenarios fill it (05), Gradle and jt judge it (01, 07).

## Packages & imports

Relative to `<ws>` = `/Users/vfyjxf/ModdingGround/`: `J` = `Project/JechGround/1.21.1/`; `G` = `J/gradle/jech-uitest.gradle` (applied by `N/build.gradle:108` last); `N` = `J/neoforge/`; `H` = `N/src/uitestMod/java/me/towdium/jecharacters/uitest/`; `SE` = `N/src/uitestModEntry/java/me/towdium/jecharacters/uitest/`; `CT` = `Project/JechGround/compat-tests/`.

| Unit | Provides | Anchor |
| --- | --- | --- |
| sourcesets, gated | `uitestMod` = engine (frame pump, scenario DSL, input/capture seam, mixins + `jechtest.mixins.json`); `uitestModEntry` = `@Mod` entry + `/jechtest` + generated `neoforge.mods.toml`, depending on the engine, never the reverse; `uitest` = scenarios via a `ScenarioRegistry` scan. `-PjechTest` guards all three and jars package only `sourceSets.main`, so none of it ships | `G:1-11, 64-96`; `N/build.gradle:105-108` |

## API quick reference

| Signature | Purpose | Contract |
| --- | --- | --- |
| run task | `neoForge.runs.clientTests { client() … }`, own game dir, `loadedMods = [jechMod, jechUiTestMod]` there versus `[jechMod]` elsewhere; full name `:neoforge:runClientTests`. Ignores `-PjechQuickPlayWorld`: it makes its own flat level instead | `G:122-147, 173-179`; `CT/envs/1.21.1-neoforge.json:18` |
| `-PjechTest` → properties | each name becomes the system property `jech.uitest.<lower-cased name>`, except `…Window=WxH` → `windowWidth`/`windowHeight` and `…KeepOpen`/`…Headless` → `…=true`; a selection also sets `jech.uitest.out` | `G:30-41, 157-168` |
| only switch, defaults | `RunConfig.prefix = "jech.uitest."`, and `requested()` = `System.getProperty(prefix+"run") != null` is the single switch. Defaults: `out` `jech-uitest`, `run` `all`, `exclude` `""`, `guiScale` 2, window 0/0 (then 1280×720), `inputMode` `SYNTHETIC`, `watchdogSec` 90, `keepOpen`/`headless` false, `scenarioTimeoutMs` 120000, `settleMs` 60, `timeoutMs` 5000, `captureOnFailure` true, `errorPolicy` `ABORT_SCENARIO` | `H/api/RunConfig.java:38-66, 100-108` |
| two-placement rule | `jechUiTestOutDir = jechRunDir == null ? rootProject.file('runs/client-tests') : new File(jechRunDir, 'uitest')`; `gameDirectory` = `-PjechRunDir` if given else that dir. `jechCleanUiTestReport` deletes only the out dir: in the bare form it lists that directory's direct children when it runs and deletes all but `jech-scenarios`, so the hot folder survives both forms; the `-PjechRunDir` form deletes `<game dir>/uitest` alone. `jechPresetUiTestOptions` is `onlyIf { jechUiTestSelection != null && jechUiTestRunDir == null }` | `G:30-33, 144-148, 185-205, 230-244` |
| `verifyJechUiTest` | reads `<outDir>/report.json`; a missing file is a failure, never a skip ⇒ `GradleException` "no UI test report at …", and any `status != 'PASS'` ⇒ `GradleException('UI test failed:\n …')`. `tasks.configureEach` wires a selected `runClientTests` to `dependsOn jechCleanUiTestReport`, `dependsOn jechPresetUiTestOptions`, `finalizedBy jechUiTestVerify` | `G:246-295, 300-306` |
| engine, frame = clock | `onFrame` runs inside a reentry guard (`inStep`) that no-ops nested frames, and an unfinished step calls `TestContext.repeat(...)` to retry next frame; never kill mid-frame, since `Minecraft#stop` ends the main loop and a shutdown hook carries the exit code out. Phases, ≤1 transition/frame: `waitGameReady, pinOptions, createWorld, awaitWorld, pinWorld, nextScenario, runScenario, finish, exiting`. `waitGameReady` also clears an `AccessibilityOnboardingScreen`, and `pinOptions` pins window size, guiScale, `pauseOnLostFocus=false`, `renderDistance=4`, `enableVsync=false` | `H/engine/UITestRunner.java:48-65, 69-83, 147-160, 253-284, 310-355, 863-883, 1002-1018` |
| `InputMode` | `synthetic` dispatches on the live screen with forged pointer/key state and never touches the OS cursor; `real` moves the OS cursor via `glfwSetCursorPos` and needs focus plus a visible window; anything unrecognised is `synthetic`. Gradle rejects `-PjechTestHeadless` + `-PjechTestInputMode=REAL` at configuration time instead of downgrading | `H/api/InputMode.java:9-20`; `G:44-51` |
| watchdog | daemon `jechtest-uitest-watchdog` ticks every 1 s on `now() - lastFrameAt <= max(1, watchdogSeconds)*1000`; a stall dumps thread stacks, writes the reports + `hang.txt`, then `halt(3)` | `H/engine/UITestRunner.java:279-284, 889-935` |
| `WorldBootstrap` | one throwaway flat creative world (`GameType.CREATIVE`, `Difficulty.PEACEFUL`, datapack `WorldPresets.FLAT`), prefix `jechtest_`, `seed = 4_948_311_507L`; `deleteStaleWorlds(source, keep)` sweeps `worldPrefix` dirs except `keep`; `rejoinLevel` = `openWorld(name, onFailure)` | `H/engine/WorldBootstrap.java:35-45, 70-105, 107-149` |
| sessions | `buildSessionPlan` buckets by `options().session()`: `shared` group → `reenter` group → each `fresh` alone. A non-head `reenter` first leaves (`ClientLevel.disconnect()` + `server.halt(false)`, then waits for `level == player == server == null`) and reopens the same save; a non-head `fresh` builds a new world; both waits are bounded by `reenterBudgetMs`/`worldBudgetMs`. Per scenario: `player.closeContainer()`, `setScreen(null)`, `MatchRecorder.clear()` | `H/engine/UITestRunner.java:372-424, 489-566, 578-648, 697-754` |
| report shape | `report.json`/`report.txt`/`junit.xml` go into `config.outDir()` before the client exits, each written synchronously as a temp sibling plus an atomic move; captures land in `screenshots/<scenario>/` (`H/api/TestContext.java:320-327, 340-343`; `H/capture/FrameCapturePipeline.java:15-19, 35-37, 182`). jt's `uitest_report` then reads `<gameDir>/uitest/report.json`, requiring `schema==1`, integer `failed`/`errored` and `status=="PASS"` | `H/engine/report/ReportWriter.java:15-46, 49-79, 126-134`; `CT/lib/checks.py:43-61, 283-337` |
| `/jechtest` | `@Mod(UiTestConstants.modId)` (`"jechtest"`), client-only, returning under `FMLEnvironment.production`; registers `/jechtest list` and `/jechtest run <greedyString>` → `UITestRunner.runInteractive(selection)`: runs inside the player's world, leaves the client with the player, refuses a second live run | `SE/UiTestCommands.java:30-61`; `H/engine/UITestRunner.java:178-236` |

## Minimal example

```bash
cd Project/JechGround/1.21.1
./gradlew :neoforge:runClientTests -PjechTest=json-smoke   # → runs/client-tests/{report.json,report.txt,junit.xml}
./gradlew :neoforge:runClientTests -PjechTest=smoke -PjechRunDir=/tmp/game
```
<!-- sample: none (data-driven JSON; no sample tree) -->

## Contracts & pitfalls

- `verifyHarnessIsolation` (`check` depends on it and on the three `compileUitest*Java` tasks) scans the built jar, so entries under `me/towdium/jecharacters/uitest/` or `…/mixin/uitest/`, or a `jechtest.mixins.json`, fail the build even when `-PjechTest` was never set (`G:308-339`).
- Out of scope: capture internals (`CaptureRequest`/`SurfaceCapture`/`LayoutTimeline`), `WorldPlayerInput` (628 lines), and the `PlayerEmbodiment`/`CursorState`/`KeyState` internals; only 1.21.1 was read.

## See also

- 01-jt-overview.md §API quick reference — the matrix from the CLI.
- 02-jt-env-authoring.md §API quick reference — the `gradle`/`uitest` blocks and the `key_*` trap.
- 04-match-recorder.md §API quick reference — the taps behind `assert_match_recorded`.
- 05-scenario-authoring.md §API quick reference — the steps `-PjechTest` carries.
- 07-verification.md §API quick reference — the layered red path.
