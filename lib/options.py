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
)


def render():
    """The preset as ``options.txt`` text (one ``key:value`` per line)."""
    return "".join(f"{key}:{value}\n" for key, value, _why in PRESET)


def keys():
    return [key for key, _value, _why in PRESET]


def explain():
    """``(key, value, why)`` triples, for the docs and the report notes."""
    return tuple(PRESET)
