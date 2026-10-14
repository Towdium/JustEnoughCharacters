"""The minimal ``options.txt`` an instance is started with, and why each key is there.

A freshly materialized Prism instance has no ``options.txt``, and a client with
no ``options.txt`` shows the **"Welcome to Minecraft!" accessibility onboarding
screen before the title screen** — a modal page with a "Continue" button that
nothing in a headless run can press. The run then never reaches the menu, let
alone a world. Presetting the file removes that page from the startup path; it is
written into the pack's ``overrides/`` and staged into the instance on every run
(``lib.instance.isolate``), so a stale instance can neither mask a broken preset
nor keep one that has drifted.

The preset is deliberately tiny — only keys that *unblock* automation, never a
preference such as volume, render distance or window size (those stay vanilla and
the env's own ``jvm.window``):

* ``onboardAccessibility:true`` — marks the accessibility onboarding as done, so
  the onboarding screen is not shown. (Field ``Options.onboardAccessibility``;
  the client shows the screen while this is false, which is also the state of a
  profile with no ``options.txt`` at all. See the Minecraft Wiki's *Title Screen*
  and *options.txt* pages, and Mojang's own reproduction steps on MC-310326,
  which open the screen by deleting ``options.txt``.)
* ``lang:zh_cn`` — starts the client in Simplified Chinese. This is not a taste
  in speech: JECh is a *pinyin* search mod, and a pinyin search only has anything
  to search once the items carry Chinese names (``block.minecraft.stone`` is
  ``石头``, the query ``shitou`` reaches it, and in ``en_us`` it is ``Stone`` and
  no pinyin can). The env that runs the in-client UI test harness asserts on
  exactly that, so the language is part of the fixture the scenario is written
  against. A version whose language code differs (1.12.2 spells it ``zh_CN``)
  falls back to ``en_us`` with a warning instead of failing to load, which is why
  one preset can serve the whole matrix.

**No `key_*` line may be preset. (Measured 2026-10-14; this is the trap to know
about.)** The preset carries no ``version:`` line, so ``Options.dataFix`` sees a
file of data-version 0 and runs the *legacy* chain — and
``OptionsKeyLwjgl3Fix.makeRule`` does ``Integer.parseInt`` on the value of every
key whose name starts with ``key_`` (that fixer exists to convert LWJGL2's
numeric key ids into ``key.keyboard.*`` names). A modern binding with a modifier
is written as ``key.keyboard.f:CONTROL``, which is not a number, so the fixer
throws:

```
[Render thread/ERROR] [net.minecraft.client.Options/]: Failed to load options
java.lang.NumberFormatException: For input string: "key.keyboard.f:CONTROL"
    at net.minecraft.util.datafix.fixes.OptionsKeyLwjgl3Fix.lambda$makeRule$1(OptionsKeyLwjgl3Fix.java:147)
```

``Options.load()`` catches it and keeps *every* option at its default, so a
preset with one such line silently discards the whole file — including ``lang``,
which is how this was found: the JEI scenario's pinyin assertion failed on a
correct build because the client had come up in ``en_us``. A ``version:`` line
with the client's current data version makes the legacy chain not run at all and
the same file loads cleanly (verified both ways), but that number is per Minecraft
version and has no cheap authoritative source here, so the preset simply stays
free of ``key_*`` lines.

Dropping the JEI focus-key line costs nothing: a fresh game directory has no
override, so the binding the scenario presses is JEI's own registered default
(``key.keyboard.f`` + ``CONTROL``) — the value the line would have pinned. A run
that has to survive a *rebound* key would need the ``version:`` line, and that is
the point at which to add one.

Keys that were considered and left out, with the reason:

* ``skipMultiplayerWarning`` — the warning it skips is only shown when joining a
  *server*; these runs join a single-player world.
* ``tutorialStep`` — the tutorial hints are toasts, not screens; they do not gate
  the menu or a world load.
* ``pauseOnLostFocus`` — pausing on focus loss does not undo a loaded level, and
  nothing in the runs asserts on gameplay ticks.

A key an older client does not know is ignored when it loads the file (the
client keeps only the keys it knows and rewrites the file on exit), so one preset
serves every version line in the matrix.
"""

from __future__ import annotations

FILENAME = "options.txt"

# (key, value, why) — the order is the order in the rendered file.
PRESET = (
    ("onboardAccessibility", "true",
     "the accessibility onboarding screen is shown while this is false; a profile "
     "with no options.txt is in exactly that state"),
    ("lang", "zh_cn",
     "the pinyin search the in-client harness asserts on needs Chinese item names "
     "(石头 for stone); a version that spells the code differently falls back to en_us"),
)


def render():
    """The preset as ``options.txt`` text (one ``key:value`` per line)."""
    return "".join(f"{key}:{value}\n" for key, value, _why in PRESET)


def keys():
    return [key for key, _value, _why in PRESET]


def explain():
    """``(key, value, why)`` triples, for the docs and the report notes."""
    return tuple(PRESET)
