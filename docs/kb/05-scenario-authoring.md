> When-to-read: before writing or changing a UI-test scenario — its JSON keys, how files are loaded, which names a step can address, and how input reaches a mod overlay instead of a vanilla widget.

## Mental model

A scenario is a declarative list of steps; the JSON layer and the Java layer end in guarded calls on the same builder, so they cannot drift. JSON scenarios come from a loaded mod's resources or a hot folder re-read on every scan, and everything about them is fail-closed. Input routing decides whether a keystroke reaches the vanilla widget or the mod overlay, and the JEI-facing scenarios hang on that choice.

## Packages & imports

Relative to `WS` = `/Users/vfyjxf/ModdingGround/`: `H` = `WS/Project/JechGround/1.21.1/neoforge/src/uitestMod/java/me/towdium/jecharacters/uitest/`; `U` = `WS/Project/JechGround/1.21.1/neoforge/src/uitest/`; `S` = `U/resources/uitest/scenarios/`, whose `README.md` is the author doc.

| Unit | Provides | Anchor |
| --- | --- | --- |
| `H/datadriven/JsonScenarioSources`, `H/ScenarioRegistry` | the two load channels and the name table | `H/datadriven/JsonScenarioSources.java:25-42, 60-88, 128-139, 159-190`; `H/ScenarioRegistry.java:24-41, 79-181` |

## API quick reference

Top-level keys (`scenarioKeys`); anything else raises `ScenarioJsonException` naming that key and the allowed set (`H/datadriven/JsonScenarioReader.java:25-28, 35-42`; `S/README.md:26-40`):

| Key | Type, default | Contract |
| --- | --- | --- |
| `name` | string, required | not blank, no `/` or `\` ("must be a plain label"); the only name the selection grammar matches |
| `group` | string, `""` | matched by `group:` |
| `tags` | string list, `[]` | matched by `tag:` |
| `requiresWorld` | bool, `true` | false runs on the title screen |
| `guiScale`, `hideGui` | int 0, bool `false` | 0 = the run's; only `>0` counts |
| `settleMs`, `timeoutMs`, `scenarioTimeoutMs` | int, 0 | 0 = the run's; `timeoutMs` = per-step wait budget, `scenarioTimeoutMs` = whole-scenario budget |
| `session` | case-insensitive `shared`/`reenter`/`fresh`, `shared` | a typo is refused, not defaulted |
| `steps` | step objects, required, non-empty | the 22 `do` names and their parameters are 06 |

| Signature | Purpose | Contract |
| --- | --- | --- |
| load channels | where files come from | (1) any loaded mod's `uitest/scenarios/*.json`, read through `IModFile.findResource`, with the runtime class path as a second view; (2) the game directory's `jech-scenarios/*.json`, re-read on every scan so a scenario needs no rebuild. `loadAll()` = mod resources then hot folder (`H/datadriven/JsonScenarioSources.java:25-42, 60-88, 128-139`; `S/README.md:9-20`). Under jt it is `<runs>/<id>/game/jech-scenarios/` (`compat-tests/lib/runner.py:314-319`; `compat-tests/lib/instance.py:234-242`); a hot file's name takes no part in matching, only naming a scenario as the fallback for an unreadable file (`H/datadriven/DataDrivenScenario.java:51-56, 60-65`) |
| fail-closed registration | a bad file is a result, not an absence | an unreadable or unparseable file, a top-level non-object, or a validation failure (unknown step, unknown key), becomes `Loaded(name, label, null, <reason>)`; a read-time refusal thus registers a fails-on-run scenario, never an absence (name from `root.name`, else the file name); `ScenarioRegistry.scan()` keeps it in the name table, and `Entry.define` turns it into `DataDrivenScenario.defineFailure(...)`. A duplicate `name` is `putIfAbsent` — first wins, with a warning (`H/datadriven/JsonScenarioSources.java:159-190`; `H/ScenarioRegistry.java:24-41, 79-121, 123-181`) |
| screen vs handler routing | which listener sees the key | **screen routing** (`type`, `type_into`, `key`) dispatches to the open screen, but an overlay that is not one of its widgets never sees it. **handler routing** (`press_key`, `press_bind`, `type_text`) goes through vanilla's `KeyboardHandler`, where NeoForge raises screen key events — which is why a mod overlay hears it. **JEI's overlay only accepts handler routing**, its field being no widget of `CreativeModeInventoryScreen`. A release follows whoever was listening, so `{"do":"key","key":"escape"}` is a complete "close this screen" step (`S/README.md:71-100`) |
| name resolution | what `screen`/`bind`/`key`/`modifiers` accept | `screen`: a fully qualified class or a bare name looked up under `net.minecraft.client.gui.screens` then `…screens.inventory`, else an error asking for a fully qualified name. `bind`: a vanilla friendly name (`chat`, `command`, `inventory`, `hotbar.1`…`hotbar.9`) else the game's own registration name such as `key.jei.focusSearch`, else a "did you mean …?" list; bindings are read **live**. `key`: a GLFW name (`f`, `escape`, `enter`, `backspace`, `space`, `left_control`); `GLFW_KEY_UNKNOWN` is an error. `modifiers`: `ctrl`/`control`, `shift`, `alt`, `super`/`meta`/`cmd`/`command` (`H/datadriven/JsonTargets.java:39-77, 79-148, 150-178`) |
| assertion filter | `assert_match_recorded` semantics | `MatchFilter(method, queryContains, candidateContains)`: every term given must hold, and a `null` term means "anything" rather than "empty"; an all-null filter is rejected. `method`: 04's label. `expect: true` waits up to `within_ms`, then fails with the recorded tail; `expect: false` is the negative control and fails as soon as a matching record exists (`H/match/MatchFilter.java:8-49`; `H/datadriven/ScenarioSteps.java:151-164`) |
| selection grammar | `-PjechTest=` / `env.uitest.selection` | `all` / `name` / `a,b` (OR) / `group:` / `tag:` / `regex:` / `mod:`, plus an optional second expression to subtract; the comma is the separator, and an empty selection is reported rather than passed. The scenario-side grammar, not 01's env-side one (`H/api/ScenarioSelection.java:9-17, 28-31, 44-48, 68-91`) |

## Minimal example

```json
{
  "name": "my-scenario", "tags": ["smoke"], "requiresWorld": true,
  "steps": [
    {"do": "open_chat"},
    {"do": "wait_until_screen", "screen": "ChatScreen"},
    {"do": "type", "text": "jech smoke"},
    {"do": "wait_until_screen", "screen": null}
  ]
}
```
<!-- sample: none (data-driven JSON; no sample tree) -->

## Contracts & pitfalls

- Three hard preconditions (`S/README.md:150-173`): the level is **creative and throwaway** (`WorldBootstrap` makes it, 03), so the inventory bind opens `CreativeModeInventoryScreen`; the client language is pinned (`lang:zh_cn` for jt, the same two-key preset for a bare harness run), which is why pinyin assertions reach `石头` and never `Stone` — that fixture and the `key_*` ban are 02's; and bindings are read live, so a scenario must **not** write `options.txt` `key_*` lines.
- The recorder runs for the whole run and is cleared per scenario, so an assertion speaks about the input this scenario just made: a scenario that produced unwanted matches (its own `/jech` command, an already-asserted search) must clear the buffer itself (04).
- Negative control, as `keyboard-switch.json` writes it: switch with the real `/jech keyboard xiaohe` command, clear the buffer (`record_matches{clear:true}`), then assert `expect:false` that the *other* layout's key order (`shitou`) reaches 石 (`S/keyboard-switch.json:27-32`; `S/README.md:142-148`).
- The `/jech …` commands a scenario types are JECh's own (`WS/Project/JechGround/1.21.1/common/src/main/java/me/towdium/jecharacters/JechCommand.java:27-54`); the harness's `/jechtest` is 03's.
- Non-goal: the Java scenario API (`ScenarioBuilder`) is not documented in this KB; only the JSON→verb mapping is in scope.

## See also

- 02-jt-env-authoring.md §API quick reference — `env.uitest.selection`, the language fixture, the `key_*` trap.
- 03-uitest-harness.md §API quick reference — the engine that runs these scenarios and sessions.
- 04-match-recorder.md §API quick reference — the two taps and the `method` labels an assertion may name.
- 06-step-vocabulary.md §API quick reference — all 22 steps and their parameters.
- 07-verification.md §Contracts & pitfalls — the negative control inventory.
