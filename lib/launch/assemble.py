"""The run's own game directory: its ``mods/``, its ``options.txt``, nothing else.

Under the direct launcher the game directory is the only per-run state there is.
Everything large lives in the shared cache (``lib.cache``) and is *linked* in, so
a run directory holds a handful of symlinks and the files the client itself
writes — which is what makes ``du`` on a run directory a meaningful number
instead of a copy of the asset store.

What must physically be inside the game directory, and why:

``mods/``
    the loader reads mods from ``<game dir>/mods`` and from nowhere else; the
    jars are symlinks to ``cache/mods/<file>``, to the subject jar and to the
    harness jar.
``options.txt``
    the fixture the client starts from (``lib.options``): without it the
    accessibility onboarding page is shown, and the language is the one the
    pinyin scenarios assert in.
``config/``, ``saves/``, ``logs/``
    created here so the first run of a version has them; the client owns them
    afterwards, and the checks read ``logs/latest.log``.
"""

from __future__ import annotations

import os
from pathlib import Path

from .. import options
from ..errors import ConfigError


class GameDir:
    def __init__(self, path, mods, options_path, notes=()):
        self.path = Path(path)
        self.mods = list(mods)
        self.options_path = Path(options_path)
        self.notes = list(notes)

    def summary(self):
        return f"game dir {self.path}: {len(self.mods)} mod jar(s), options.txt written"


def assemble(game_dir, *, mc, mod_jars, options_extra=None, log=print):
    """Build the run's game directory; returns what was assembled.

    ``mod_jars`` is the full set the game will load: the subject jar, the env's
    mods, and the harness. The order is not meaningful to the loader; the count
    and the names are logged, which is how a reader of the run finds out what was
    actually under test.
    """
    game_dir = Path(game_dir)
    mods_dir = game_dir / "mods"
    mods_dir.mkdir(parents=True, exist_ok=True)
    for name in ("config", "logs", "saves"):
        (game_dir / name).mkdir(parents=True, exist_ok=True)

    linked = []
    for jar in mod_jars:
        jar = Path(jar)
        if not jar.is_file():
            raise ConfigError(f"the jar to load {jar} does not exist, so the game directory cannot "
                              "be assembled")
        link = mods_dir / jar.name
        if link.is_symlink() or link.exists():
            link.unlink()
        os.symlink(jar, link)
        linked.append(jar.name)

    options_path = game_dir / options.FILENAME
    options_path.write_text(options.render(mc, extra=options_extra), encoding="utf-8")
    log(f"assemble: {game_dir}: mods/ <- {', '.join(linked) or '(none)'}; "
        f"{options.FILENAME} written ({len(options.keys())} preset key(s)"
        + (f" + {len(options_extra)} from the env)" if options_extra else ")"))

    notes = []
    if not linked:
        notes.append("mods/ holds no jar: this run loads nothing but the loader itself")
    return GameDir(game_dir, linked, options_path, notes)
