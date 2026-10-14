> When-to-read: you need the exact `do` name a JSON step uses, its required and optional arguments, a default, or the builder verb behind it — the whole 22-step vocabulary in one table.

## Mental model

The vocabulary is one `LinkedHashMap` in `ScenarioSteps`, so declaration order is also the order `describeVocabulary()` and `names()` report. A handler is never more than a guarded call into `ScenarioBuilder` — the same verbs the Java scenarios use — so the JSON and Java layers cannot drift, and adding a step is one more `register(...)` line. Required parameters are read with `args.string/integer/number(...)`, where an absent key is an error; optional ones with `args.stringOr/boolOr/integerOr/stringsOrEmpty`, which carry the default. `args.done()` then refuses any key the handler never read, because there is deliberately no "ignore what you do not understand" path.

## Packages & imports

Relative to `WS` = `/Users/vfyjxf/ModdingGround/`: `H` = `WS/Project/JechGround/1.21.1/neoforge/src/uitestMod/java/me/towdium/jecharacters/uitest/`; `S` = `WS/Project/JechGround/1.21.1/neoforge/src/uitest/resources/uitest/scenarios/`.

| Unit | Provides | Anchor |
| --- | --- | --- |
| `H/datadriven/ScenarioSteps` | the vocabulary and its handlers, in declaration order | `H/datadriven/ScenarioSteps.java:41-183, 188-215, 224-231` |
| `H/datadriven/Args` | the per-step argument reader: presence, type and `done()` | `H/datadriven/Args.java:31-173` |
| `S/README.md` | the author-facing copy of the same list | `S/README.md:42-142` |

## API quick reference

Each name is registered once, in the order below; that order is also the order `describeVocabulary()` prints and the order a JSON file's steps may use. Required = the handler reads it with `args.string/integer/number(...)` (absent, wrongly typed or, for `string`, empty is refused); optional = `args.stringOr/boolOr/integerOr/stringsOrEmpty` with the default shown. The number after each `do` is its line in `H/datadriven/ScenarioSteps.java`.

| `do` (line) | Required | Optional (default) | Verb | Contract |
| --- | --- | --- | --- | --- |
| `log` (44) | `text` | — | `log` | one line into the step log |
| `settle` (45) | `ms` | — | `settleMs` | quiet time applied to the steps that follow it |
| `wait_ms` (46) | `ms` | — | `waitMs` | a wall-clock wait |
| `wait_frames` (47) | `frames` | — | `frames` | waits that many rendered frames |
| `open_chat` (49-52) | — | — | `pressBind(options -> options.keyChat)` + `awaitScreen(ChatScreen.class)` | opens chat through the real chat binding |
| `close_screen` (54) | — | — | `closeScreen` | closes the screen that is open |
| `wait_until_screen` (56-73) | `screen` (JSON `null` allowed) | — | `waitUntil` + `checkScreen` | waits for that screen class to be open; `screen: null` waits for *no* screen, which is how a close is proved |
| `press_bind` (75-76) | `bind` | — | `pressBind(JsonTargets.bind(...))` | handler routing: presses a binding, addressed by friendly name or registration name |
| `press_key` (78-84) | `key` | `modifiers`, `times` (1) | `pressKey(key, mask)` ×`times` | handler routing: a key, its named modifiers pressed for real |
| `key` (86-92) | `key` | `modifiers`, `times` (1) | `key(key, mask)` ×`times` | screen routing: a key |
| `enter` (94) | — | — | `key(GLFW.GLFW_KEY_ENTER, 0)` | Enter, screen routing |
| `type` (96) | `text` | — | `type` | screen routing: types character by character |
| `type_text` (97) | `text` | — | `typeText` | handler routing: types text |
| `type_into` (98) | `text` | — | `typeInto` | Ctrl+A, backspace, then screen-routed text |
| `click` (100-123) | `x`, `y` | `button` (`"left"`), `shift` (`false`), `double` (`false`) | `doubleClick` / `shiftClick` / `click` / `rightClick` / `clickAt(x,y,2)` | GUI-scale coordinates; `double` short-circuits, else `shift` picks `shiftClick`; `button` ∈ left/right/middle |
| `command` (125-132) | `text` | — | `pressBind(keyChat)` → `awaitScreen(ChatScreen)` → `type` → `key(ENTER)` → wait for no screen | sends a `/jech …` client command through real chat |
| `record_matches` (134-149) | `enable` and/or `clear` (≥1 named) | — | `recordMatches(enable)` / `clearRecordedMatches()` | naming neither is refused — "a step that does neither is a no-op"; a named key is a boolean read with default `false` |
| `assert_match_recorded` (151-164) | `method` / `query_contains` / `candidate_contains` (≥1) | `expect` (`true`), `within_ms` (0) | `assertMatchRecorded(filter, expect, within_ms)` | asserts against the recorder; an all-`null` filter is refused; labels are 04's |
| `screenshot` (166) | `label` | — | `screenshot` | one full-frame capture into `screenshots/<scenario>/` |
| `teleport` (168-169) | `x`, `y`, `z` | — | `teleportPlayer` | moves the player |
| `give` (171-180) | `item` | `count` (1) | `giveItem` | gives an item; `item` goes through `ResourceLocation.parse`, an unparsable id is refused |
| `run_server_command` (182) | `text` | — | `runCommand` | a server command, bypassing all input |

## Minimal example

```json
{
  "name": "vocabulary-smoke",
  "steps": [
    {"do": "log", "text": "a required key and an optional one in one file"},
    {"do": "press_key", "key": "e", "times": 2},
    {"do": "wait_until_screen", "screen": "CreativeModeInventoryScreen"},
    {"do": "close_screen"},
    {"do": "wait_until_screen", "screen": null}
  ]
}
```
<!-- sample: none (data-driven JSON; no sample tree) -->

## Contracts & pitfalls

- A `do` outside this table is refused while the scenario is read, and the refusal names both the step and the whole vocabulary (`H/datadriven/JsonScenarioReader.java:103-123`; `H/datadriven/ScenarioSteps.java:188-201`).
- The repeat parameter shared by `press_key` and `key` is `integerOr("times", 1)` bounded to 1..64; out of range raises `"'times' must be between 1 and 64, got N"` (`H/datadriven/ScenarioSteps.java:224-231`).
- `wait_until_screen`'s `screen` is required to be present but may be JSON `null`: `args.has("screen")` separates "wrote null on purpose" from "forgot the key" (`H/datadriven/ScenarioSteps.java:56-63`; `H/datadriven/Args.java:141-149`).
- A parameter a handler never read fails the step with the unknown keys and the keys the step does take, so a misspelled key names itself instead of silently doing nothing (`H/datadriven/Args.java:151-166`).
- `ScenarioSteps` is a package-private `final` class: this vocabulary is not a public API, and neither the `ScenarioBuilder` Java API nor `Args`' per-key wording is covered here (`H/datadriven/ScenarioSteps.java:32`).

## See also

- 04-match-recorder.md §API quick reference — the recorder, its taps and the ten labels `method` may name.
- 05-scenario-authoring.md §API quick reference — the scenario object, the two load channels and which names a step can address.
- 07-verification.md §Contracts & pitfalls — the layered red a failing step produces.
- `S/README.md` §Steps `:42-148` — the same list for authors, with the routing notes.
