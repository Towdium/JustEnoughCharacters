# JSON scenarios — the declarative step vocabulary

A scenario written as JSON needs no Java: it is a list of steps, each one a name from the table
below plus that step's parameters. The steps are the same verbs the Java scenarios are written with
(`me.towdium.jecharacters.uitest.api.ScenarioBuilder`), so the two layers cannot mean different
things; adding a Java step and adding a JSON step name is the same edit to
`me.towdium.jecharacters.uitest.datadriven.ScenarioSteps`.

## Where a file is picked up

Two folders are read, every time the registry scans:

| Where | Path | For |
| --- | --- | --- |
| inside a mod | `uitest/scenarios/*.json` in any loaded mod's resources | scenarios that ship with the harness (this folder) |
| the game directory | `<game dir>/jech-scenarios/*.json` | scenarios to try out without rebuilding |

Both channels are fail-closed: a file that is present and cannot be parsed is **registered as a
scenario that fails when it runs**, naming the file and the problem, and the run logs it either way.
It is never dropped — a mistyped step must not become a test that passes because it never ran.

A file is one JSON object. Unknown keys are refused at every level (scenario keys, and each step's
parameters), because a silently ignored key is a scenario that quietly does something else than its
author wrote.

## The scenario object

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `name` | string | — | the unique name the selection grammar matches (`-PjechTest=<name>`, `tag:`, …) |
| `group` | string | `""` | grouping label, matched by `group:` |
| `tags` | list of strings | `[]` | matched by `tag:` |
| `requiresWorld` | boolean | `true` | whether the scenario needs a world (a world-less one runs on the title screen) |
| `guiScale` | number | run's | GUI scale to pin for this scenario |
| `hideGui` | boolean | `false` | hide the in-game HUD while it runs |
| `settleMs` | number | run's | quiet time after every step |
| `timeoutMs` | number | run's | wait budget for a step that repeats |
| `scenarioTimeoutMs` | number | run's | budget for the whole scenario |
| `session` | `shared` \| `reenter` \| `fresh` | `shared` | the least client session this scenario needs |
| `steps` | list of step objects | — | the steps, in order |

## Steps

Every step is `{"do": "<name>", …parameters}`. A parameter marked *(required)* must be present.

### Waiting and bookkeeping

| `do` | Parameters | Engine verb |
| --- | --- | --- |
| `log` | `text` *(required)* | `log` — writes a line into the step log |
| `settle` | `ms` *(required)* | `settleMs` — the quiet time applied to the steps **after** this one |
| `wait_ms` | `ms` *(required)* | `waitMs` — wall-clock wait |
| `wait_frames` | `frames` *(required)* | `frames` — rendered-frame wait |

### Screens

| `do` | Parameters | Engine verb |
| --- | --- | --- |
| `wait_until_screen` | `screen` *(required, may be `null`)* | `waitUntil` + `checkScreen`; `null` waits for **no** screen to be open, which is how a scenario proves a screen closed |
| `open_chat` | — | `pressBind(keyChat)` + `awaitScreen(ChatScreen)` — the real chat bind, not `setScreen` |
| `close_screen` | — | `closeScreen` |

A `screen` name is a screen class: a fully qualified name, or a bare class name looked up in
`net.minecraft.client.gui.screens` and `…​.inventory`. **Name the screen the run really opens, not the
one a player normally sees:** this harness's throwaway level is created in **creative**
(`WorldBootstrap` pins `GameType.CREATIVE`), so the inventory bind opens
`CreativeModeInventoryScreen`, which is not an `InventoryScreen`. A scenario that waits for the
wrong class times out after its `timeoutMs` and captures the screen it did get — which is the
cheapest way to find the right name.

### Keys and text

Two routes exist, and which one to use matters:

* the **screen route** (`type`, `type_into`, `key`) dispatches at the open screen, which is what a
  vanilla widget reads — but an overlay that is not a widget of that screen never sees it;
* the **handler route** (`press_key`, `press_bind`, `type_text`) goes through vanilla's own
  `KeyboardHandler`, which is also where NeoForge raises its screen key events — so it is the route
  a mod's overlay (JEI's search bar, say) actually hears.

A **release** follows whatever is listening when it arrives: the open screen, or — when the press
was what closed the screen (Escape closing a chat box, Enter sending the line) — the in-world
handler route. So `{"do": "key", "key": "escape"}` is a complete "close this screen" step, and it
does not need a matching release written out afterwards.

| `do` | Parameters | Engine verb |
| --- | --- | --- |
| `press_bind` | `bind` *(required)* | `pressBind` — the bind by a friendly name (`inventory`, `chat`, `forward`, `hotbar.1`, …) **or by the name the game knows it by** (`key.jei.focusSearch`). A bind a mod declared with a modifier has that modifier held around the press, as a player's chord would. |
| `press_key` | `key` *(required)*, `modifiers`, `times` | `pressKey` — the handler route, with any named modifiers held as real keys |
| `key` | `key` *(required)*, `modifiers`, `times` | `key` — the screen route |
| `type` | `text` *(required)* | `type` — the screen route, one character per step |
| `type_text` | `text` *(required)* | `typeText` — the handler route |
| `type_into` | `text` *(required)* | `typeInto` — Ctrl+A, backspace, then the screen route |
| `enter` | — | `key(GLFW_KEY_ENTER)` — the Enter key, the screen route |

* `key` names are GLFW's: `f`, `escape`, `enter`, `backspace`, `space`, `left_control`, `up`, …
  (they resolve against `GLFW.GLFW_KEY_*`).
* `modifiers` is a list of `ctrl`, `shift`, `alt`, `super`.
* `times` (1–64, default 1) repeats the gesture — pressing backspace 24 times is how a scenario
  clears a field it cannot see inside, for instance.

### Mouse

| `do` | Parameters | Engine verb |
| --- | --- | --- |
| `click` | `x`, `y` *(required)*, `button` (`left`\|`right`\|`middle`), `shift`, `double` | `click`/`rightClick`/`clickAt`/`shiftClick`/`doubleClick` — all in GUI-scaled screen space |

### World and server

| `do` | Parameters | Engine verb |
| --- | --- | --- |
| `teleport` | `x`, `y`, `z` *(required)* | `teleportPlayer` |
| `give` | `item` *(required)*, `count` | `giveItem` |
| `run_server_command` | `text` *(required)* | `runCommand` — a server command, not typed anywhere |
| `command` | `text` *(required)* | the **real chat chain**: press the chat bind, wait for `ChatScreen`, type the line, press Enter, wait for the chat to close. This is how a `/jech …` client command is issued. |

### Match recording

JECh's search decisions can be asserted on directly instead of inferred from a screenshot. The
recorder is on for the whole of a harness run and is cleared as each scenario starts, so an
assertion is always about the input this scenario made.

| `do` | Parameters | Engine verb |
| --- | --- | --- |
| `record_matches` | `enable` and/or `clear` *(at least one)* | `recordMatches` / `clearRecordedMatches` |
| `assert_match_recorded` | `method`, `query_contains`, `candidate_contains` *(at least one)*, `expect` (default `true`), `within_ms` | `assertMatchRecorded` |

* `method` picks the tap: `contains`, `containsSeq`, `containsIgnoreCase`, `equals`, `matches`,
  `startsWith`, `startsWithAt`, `rank`, `matcher` (the `Match` funnel the ASM adapters use), or
  `TreeSearcher.search` (the PinIn tree JECh's JEI integration searches with).
* `expect: true` waits up to `within_ms` (default 3000 ms) for a matching record and then fails,
  reporting the tail of what *was* recorded.
* `expect: false` is the negative control: it decides at once, failing the moment a matching record
  exists.

### Captures

| `do` | Parameters | Engine verb |
| --- | --- | --- |
| `screenshot` | `label` *(required)* | `screenshot` — one full-frame capture, written under `screenshots/<scenario>/` a frame later |

## Worked example

`keyboard-switch.json` in this folder is the reference: it switches JECh to 小鹤双拼 with a real
`/jech` chat command, opens the creative inventory the harness's world puts the player in, focuses
JEI's search bar through JEI's own bind, types the 小鹤 key order for 石头 (`uitz`), asserts the
engine matched it, and then asserts that 全拼's key order does **not** reach 石 — the negative
control that shows the switch actually happened.

## What a scenario is written against

Three facts about the run shape every scenario, and each one is a place a scenario written from
memory goes wrong:

* **the level is creative and throwaway.** `WorldBootstrap` creates it with `GameType.CREATIVE` and
  the whole run deletes it afterwards, so the inventory bind opens `CreativeModeInventoryScreen`,
  the player can fly and nothing placed earlier survives a `fresh` session.
* **the client starts in the language the launcher pinned** (`lang:zh_cn` in a `jt` run, and in a
  bare `:neoforge:runClientTests` run too — the harness's own gradle wiring writes the same two-key
  preset). A pinyin search only has Chinese names to search, so a query like `shitou` reaches 石头
  there and nothing in `en_us`; a scenario that asserts on Chinese names depends on that pin, and a
  run that shows English item names has lost the preset rather than found a bug in the engine.
* **a bind is read live, never assumed.** `press_bind` looks the mapping up in the game's own
  registry and presses the key (and modifier) that mapping currently carries, so nothing pins JEI's
  Ctrl+F: a fresh game directory already carries JEI's own registered default. That also means a
  scenario never needs an `options.txt` `key_*` line — and must not have one, because a preset
  without a `version:` line goes through the legacy key datafixer, which throws on a modified
  binding and makes the client discard the entire file (see `lib/options.py` in the compatibility
  harness).
* **the match recorder is on for the whole run and cleared per scenario**, so `assert_match_recorded`
  is always about the input this scenario made — but only if the scenario clears the buffer itself
  after a step that makes matches it does not want to see (a `/jech` command, a search it already
  asserted on).
