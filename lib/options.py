"""The minimal ``options.txt`` a run's game directory is started with, and why each key is there.

A freshly created game directory has no ``options.txt``, and a client with
no ``options.txt`` shows the **"Welcome to Minecraft!" accessibility onboarding
screen before the title screen** — a modal page with a "Continue" button that
nothing in a headless run can press. The run then never reaches the menu, let
alone a world. Presetting the file removes that page from the startup path; it is
written into the pack's ``overrides/`` and staged into the game directory on every
run (``lib.instance.isolate``), so a previous run's copy can neither mask a broken
preset nor keep one that has drifted.

The preset is deliberately tiny — only keys that *unblock* automation, never a
preference such as volume, render distance or window size (those stay vanilla):

* ``onboardAccessibility:true`` — marks the accessibility onboarding as done, so
  the onboarding screen is not shown. (Field ``Options.onboardAccessibility``;
  the client shows the screen while this is false, which is also the state of a
  profile with no ``options.txt`` at all. See the Minecraft Wiki's *Title Screen*
  and *options.txt* pages, and Mojang's own reproduction steps on MC-310326,
  which open the screen by deleting ``options.txt``.)
* ``lang:<code>`` — starts the client in Simplified Chinese. This is not a taste
  in speech: JECh is a *pinyin* search mod, and a pinyin search only has anything
  to search once the items carry Chinese names (``block.minecraft.stone`` is
  ``石头``, the query ``shitou`` reaches it, and in ``en_us`` it is ``Stone`` and
  no pinyin can). The env that runs the in-client UI test harness asserts on
  exactly that, so the language is part of the fixture the scenario is written
  against.

  **The code is per version, and a wrong one silently means English.** Vanilla
  renamed its language files to lower case in 1.13, so the same language is
  ``zh_cn`` from 1.13 on and ``zh_CN`` in 1.12.2 — and nothing normalises it:
  ``GameSettings.loadOptions`` stores the option verbatim (``if ("lang".equals(s1))
  { this.language = s2; }``, ``GameSettings.java:944-947``) and
  ``LanguageManager.onResourceManagerReload`` loads the locale data of exactly the
  code it was given, plus ``en_us`` (``LanguageManager.java:60-69``). With the
  wrong case only ``en_us`` is found, the items come up in English, and the
  harness's pinyin assertion fails on a build that is perfectly correct. Hence
  ``LANGUAGE_CODE`` below: one entry per version that spells it differently.
  (``LanguageManager.getCurrentLanguage`` does fall back to ``en_us`` for the
  window's own chrome, which is why the mistake is invisible until a scenario
  looks at an item name.)

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

# The Simplified-Chinese language code a Minecraft version spells, for the versions
# that do not use the post-1.13 lower-case one. See the ``lang`` bullet above: the
# code names the pack's ``assets/minecraft/lang/<code>.lang`` file, and the client
# stores the option verbatim, so the case is load-bearing.
LANGUAGE_CODE = {
    "1.12.2": "zh_CN",
}
DEFAULT_LANGUAGE_CODE = "zh_cn"

# (key, value, why) — the order is the order in the rendered file.
PRESET = (
    ("onboardAccessibility", "true",
     "the accessibility onboarding screen is shown while this is false; a profile "
     "with no options.txt is in exactly that state"),
    ("lang", "<code>",
     "the pinyin search the in-client harness asserts on needs Chinese item names "
     "(石头 for stone); the code is the version's own spelling, see LANGUAGE_CODE"),
)


def language_code(mc=None):
    """The language code to start ``mc`` in (the default for an unknown version)."""
    return LANGUAGE_CODE.get((mc or "").strip(), DEFAULT_LANGUAGE_CODE)


def preset(mc=None):
    """``(key, value, why)`` triples for ``mc``, with the language code filled in."""
    code = language_code(mc)
    return tuple((key, code if value == "<code>" else value, why) for key, value, why in PRESET)


def render(mc=None):
    """The preset as ``options.txt`` text (one ``key:value`` per line)."""
    return "".join(f"{key}:{value}\n" for key, value, _why in preset(mc))


def keys():
    return [key for key, _value, _why in PRESET]


def explain(mc=None):
    """``(key, value, why)`` triples, for the docs and the report notes."""
    return preset(mc)
