> When-to-read: before changing what the harness can observe — the match recorder, its mixins, the input-seam mixins — or when you need the exact `method` label a scenario asserts on.

## Mental model

A scenario cannot assert only what rendered; `assert_match_recorded` asserts that the engine really compared something. Two taps feed the recorder: every static method of `me.towdium.jecharacters.utils.Match` (the funnel all ASM adapters are rewritten through) and the `TreeSearcher.search` call inside JECh's own `JechSearchStorage` (JEI's PinIn tree, which bypasses `Match` entirely). The recorder is bounded and off until a run starts, so a normal client pays nothing. PinIn itself cannot be mixin'd — a measured negative result — which is exactly why both taps sit on JECh's classes.

## Packages & imports

Relative to `WS` = `/Users/vfyjxf/ModdingGround/`: `H` = `WS/Project/JechGround/1.21.1/neoforge/src/uitestMod/java/me/towdium/jecharacters/uitest/`; `MX` = `WS/Project/JechGround/1.21.1/neoforge/src/uitestMod/java/me/towdium/jecharacters/mixin/uitest/`; `R` = `WS/Project/JechGround/1.21.1/neoforge/src/uitestMod/resources/`.

| Unit | Provides | Anchor |
| --- | --- | --- |
| `H/match/MatchRecorder`, `H/match/MatchFilter` | the bounded record buffer and the filter an assertion matches against | `H/match/MatchRecorder.java:13-44, 56-73, 85-163, 226-234`; `H/match/MatchFilter.java:8-49` |

## API quick reference

| Signature | Purpose | Contract |
| --- | --- | --- |
| the two taps | what gets recorded | (a) every static of `me.towdium.jecharacters.utils.Match`; (b) the `TreeSearcher.search` call inside `JechSearchStorage.getSearchResults` — the JEI integration goes through PinIn's tree and never through `Match` | `H/match/MatchRecorder.java:13-44` |
| bounded, off by default | cost control | `records` = `ArrayDeque(capacity=4_096)` ring (full → `removeFirst`); `keys` = `IdentityHashMap` with `maxRememberedValues=200_000` (past the cap only already-seen values are appended); `maxText=512`; `maxDescribed=8`. `private static volatile boolean enabled` with `isEnabled()` first in every entry point: the engine enables it (`startRecording`) when the run really starts and disables it in `finish()`. `setEnabled(false)` keeps the records, `clear()` empties them; all thread-safe, because `Match` is called on search threads | `H/match/MatchRecorder.java:56-73, 85-163` |
| `recordSearch(token, results)` | makes a search readable | candidate = the registration keys remembered for the returned values, up to 16 joined; a value never seen at registration yields `"<n> result(s)"` instead (a visibly different candidate, never a silent pass); `searchMethod = "TreeSearcher.search"` is the label the scenario files and the step vocabulary use | `H/match/MatchRecorder.java:147-163` |
| `MatchMixin` shape | one injection per method | `@Mixin(Match.class)`, one `@Inject` per method at `@At("RETURN")`: query, candidate and answer are all in scope there, so a record is atomic. A head+return pair would need per-thread state and mis-match nested calls (`matcher`→`matches`, `contains(CharSequence,CharSequence)` delegating to `contains(String,CharSequence)`) or throwing ones; a call that throws records nothing | `MX/MatchMixin.java:13-123` |
| `JechSearchStorageMixin` | the JEI-side tap | `@Inject(method = "put", at = @At("HEAD"))` → `MatchRecorder.rememberKey(value, key)`: registering a key in the tree is not a comparison, it only makes later candidates readable. `@Redirect` on `getSearchResults`' `INVOKE` of `TreeSearcher.search(String)` runs the original `tree.search(token)` and then `recordSearch(token, results)`. `getAllElements` (an empty-string query) is deliberately **not** tapped — it would prefix every assertion with "everything matched" | `MX/JechSearchStorageMixin.java:14-67` |
| PinIn cannot be mixin'd | measured negative result | PinIn sits on the legacy classpath and FML's mixin transformer rewrites only classes it loads itself, so a `@Mixin` on `me.towdium.pinin.searchers.TreeSearcher` is prepared and silently never applied: with `-Dmixin.debug.export=true` the export held `me/towdium/jecharacters/utils/Match.class` and **nothing** under `me/towdium/pinin/`, and the recorder stayed empty through a whole real JEI search — which is why both taps sit on JECh's own classes. [defer: re-run to reproduce] | `MX/JechSearchStorageMixin.java:35-41`; `H/match/MatchRecorder.java:28-29` |
| `jechtest.mixins.json` | the config | `required: true`, `package: me.towdium.jecharacters.mixin`, `compatibilityLevel: JAVA_21`, `minVersion: 0.8`, `mixins: []`, `injectors.defaultRequire = 1`; 12 client mixins: the two taps, six input-seam mixins and four accessors (`ChatComponentAccessor`, `KeyMappingAccessor`, `KeyboardHandlerAccessor`, `MouseHandlerAccessor`) | `R/jechtest.mixins.json:1-25` |
| input seams | scriptable input, and its gate | Six client mixins answer vanilla input from the harness state (`InputConstantsMixin`/`ScreenModifierMixin` → `KeyState`, `MouseHandlerMixin` → `CursorState`, `MinecraftMixin` → the window is active) and gate the raw handlers at HEAD while `RawInputGate.blocksNow()`. That gate is separate from `CursorState`, so a human can still abort by real keyboard | `MX/InputConstantsMixin.java:29-38`; `MX/MouseHandlerMixin.java:41-85`; `MX/KeyboardHandlerMixin.java:27-50`; `H/input/RawInputGate.java:7-34, 35-91` |

### `MatchMixin` injection table (`MX/MatchMixin.java:13-123`)

| Method descriptor | Recorded label | Note |
| --- | --- | --- |
| `contains(Ljava/lang/String;Ljava/lang/CharSequence;)Z` | `contains` | |
| `contains(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z` | `containsSeq` | |
| `contains(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z` | `ignoreCase ? "containsIgnoreCase" : "containsSeq"` | label depends on the argument |
| `equals(Ljava/lang/String;Ljava/lang/Object;)Z` | `equals` | |
| `matches(Ljava/lang/String;Ljava/lang/String;)Z` | `matches` | |
| `startsWith(Ljava/lang/String;Ljava/lang/String;)Z` | `startsWith` | |
| `startsWith(Ljava/lang/String;Ljava/lang/String;I)Z` | `startsWithAt` | |
| `rank(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)I` | `rank` | a hit iff `returnValueI() != 0` |
| `matcher(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;` | `matcher` | `Match.matcher` returns one of two shared matchers (hit `"a"` / miss `""`), so the boolean is read from the returned matcher's region — `regionEnd() > regionStart()`; probing `matches()` would consume the state the caller is about to read |

The ten labels an author can name in `assert_match_recorded.method` are therefore `contains`, `containsSeq`, `containsIgnoreCase`, `equals`, `matches`, `startsWith`, `startsWithAt`, `rank`, `matcher` and `TreeSearcher.search` (`H/datadriven/ScenarioSteps.java:151-164`).

## Minimal example

```bash
cd Project/JechGround/1.21.1
./gradlew :neoforge:runClientTests -PjechTest=keyboard-switch   # both taps record; assertions in report.json
```
<!-- sample: none (data-driven JSON; no sample tree) -->

## Contracts & pitfalls

- Registration is not a comparison: `put` only remembers keys, so "the engine compared X" needs a cleared buffer (`record_matches{clear:true}`) and a `query_contains`/`candidate_contains` assertion — the authoring recipe is 05.
- The recorder's lifecycle is the run's: on when the run starts, off inside `finish()`, emptied at the start of every scenario (03), so an assertion speaks about the input this scenario just made.
- Off by default is a performance contract, not a preference: `isEnabled()` is the first statement of every entry point, so a client running without `-PjechTest` never builds a record.

## See also

- 03-uitest-harness.md §API quick reference — the run task, the engine that enables the recorder, the report.
- 05-scenario-authoring.md §API quick reference — `record_matches` / `assert_match_recorded` in a scenario.
- 06-step-vocabulary.md §API quick reference — the 22 steps, including both recorder steps.
- 07-verification.md §API quick reference — the negative control that proves a keyboard switch happened.
