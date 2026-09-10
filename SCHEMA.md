# Target config schema

Every `generate.yaml` is a mapping of **category** to a list of **targets**.
Categories are shared by name across all Minecraft versions; a build unions
every file that applies to it and de-duplicates.

## Categories

| Category     | Transformer                | Rewrites                                             |
|--------------|----------------------------|------------------------------------------------------|
| `contains`   | `ContainsTransformer`      | `String.contains`, `String.equals`                    |
| `equals`     | `EqualsTransformer`        | `String.equals` only                                  |
| `regExp`     | `RegExpTransformer`        | `Pattern.matcher`, `String.matches`                   |
| `startsWith` | `StartsWithTransformer`    | `String.startsWith`                                   |
| `suffix`     | `SuffixArrayTransformer`   | the vanilla suffix-array search structure             |
| `strsKt`     | *(1.12.2 only)*            | `kotlin/text/StringsKt.contains`                      |

`strsKt` is deliberately **not** folded into `startsWith`. Both look like prefix
matching, but `startsWith` rewrites `java/lang/String.startsWith` while `strsKt`
rewrites `kotlin/text/StringsKt.contains`; merging them would rewrite the wrong
call sites. Only the 1.12.2 module consumes `strsKt`.

## Target format — unified (every version except 1.12.2)

```
<owner>:<methodName><jvmDescriptor>
```

```yaml
contains:
  - "mezz.jei.common.search.ElementSearchLowMem:matches(Ljava/lang/String;Lmezz/jei/core/search/PrefixInfo;Lmezz/jei/common/ingredients/IListElementInfo;)Z"
```

* `owner` — dot-separated class name; `$` separates inner classes.
* `methodName` — the method name.
* `jvmDescriptor` — the full JVM method descriptor, including the return type.

Because the descriptor is present, `TransformTarget.matches` can compare
`MethodNode.desc` exactly. This is what lets a single shared list target
overloads precisely.

## Target format — legacy (1.12.2 native)

```
<owner>:<methodName>
```

```yaml
contains:
  - "mezz.jei.ItemFilter$FilterPredicate:stringContainsTokens"
```

No descriptor. The 1.12.2 `MethodDecoder` matches on method *name* only, which is
more forgiving: a 1.12.2 entry survives a mod update that changes a signature.
That tolerance is the reason the 1.12.2 data is kept in its native form rather
than being forced through the unified shape.

## The two 1.12.2 files

`1.12.2/legacy-targets.yaml` is authoritative. It is a verbatim extraction of the
string arrays that used to be hard-coded in `JechConfig.Item#getDefault()`, and
it is what the 1.12.2 module actually loads at build time.

`1.12.2/generate.yaml` and `1.12.2/unresolvable.yaml` are derived, by
`tools/convert_targets.py upgrade`:

* entries whose `(owner, methodName)` also appears in a modern config are
  upgraded to the unified shape and land in `generate.yaml`;
* everything else lands in `unresolvable.yaml` in legacy form.

The split is not "done" vs "not done" — it is a report. A 1.12.2 target whose
class is `net.minecraft.client.gui.inventory.GuiContainerCreative` will never
match a modern config, because modern configs name that class
`net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen`. Those
entries are not broken; they are simply only meaningful to 1.12.2.

Never hand-write a descriptor into `1.12.2/unresolvable.yaml` to "fix" it. A
guessed descriptor makes the transformer silently stop firing, which is worse
than a name-only match that keeps working.

## `mapping.yaml`

A flat map applied **only when the consuming project is a Fabric target**, before
`targets.json` is written. It rewrites Mojang-mapped class names to intermediary
so one list can serve both loaders.

```yaml
"Lnet/minecraft/world/item/ItemStack":
  mcp: "Lnet/minecraft/item/ItemStack"
  intermediary: "Lnet/minecraft/class_1799"
```

## Validation

The Gradle codegen task rejects an entry unless it splits into exactly two
`:`-separated parts and neither part contains a space. `tools/verify_config.py`
in the superproject applies the same rules plus descriptor well-formedness, and
runs in CI.
