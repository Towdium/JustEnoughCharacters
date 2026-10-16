"""The JVM command line, and starting the client with this process as its parent.

The command is the vanilla one, assembled from the metadata both layers handed
over: the version's ``arguments.jvm`` (rule-filtered for this machine, so macOS
gets ``-XstartOnFirstThread`` and every platform gets its own
``-Djava.library.path``/``-cp``), the loader's JVM arguments, and the harness's
``-D`` properties. ``${...}`` placeholders are filled from :func:`values`, and an
unknown one is an error rather than a literal passed to the client.

Two consequences of launching directly, both of them simplifications:

* the client *is* the process this harness started, so the run's exit code is the
  client's own rather than a wrapper's, and the game directory in the process
  table is this run's for real;
* there is no argument file, so the argv in ``ps`` is the argv the client got.
"""

from __future__ import annotations

import os
import subprocess
from pathlib import Path

from .. import __version__, cache, paths
from ..errors import DependencyError
from . import vanilla

# What the client is told about its player. A dev client authenticates nothing;
# these are the values a launcher uses for an offline session, and the harness's
# scenarios never touch the account.
PLAYER_NAME = "Dev"
PLAYER_UUID = "00000000-0000-0000-0000-000000000000"
ACCESS_TOKEN = "0"
USER_TYPE = "legacy"


def values(*, client, loader, game_dir, natives_dir, classpath):
    """The placeholder values the metadata asks for."""
    return {
        "auth_player_name": PLAYER_NAME,
        "auth_uuid": PLAYER_UUID,
        "auth_access_token": ACCESS_TOKEN,
        "auth_session": ACCESS_TOKEN,
        "clientid": "",
        "auth_xuid": "",
        "user_type": USER_TYPE,
        "version_name": loader.profile_id if loader is not None else client.mc,
        "version_type": "release",
        "game_directory": str(game_dir),
        "assets_root": str(client.assets_dir),
        "assets_index_name": client.asset_index_id or "legacy",
        "natives_directory": str(natives_dir),
        "launcher_name": "jt",
        "launcher_version": __version__,
        "classpath": os.pathsep.join(str(entry) for entry in classpath),
        "classpath_separator": os.pathsep,
        "library_directory": str(cache.libraries_dir(client.mc)),
    }


def build_command(*, java, client, loader, game_dir, natives_dir, extra_jvm=(),
                  extra_game=(), features=None, log=print):
    """The full argv for one launch; returns ``(command, values)``."""
    path = list(client.classpath) + (list(loader.classpath) if loader is not None else [])
    filled = values(client=client, loader=loader, game_dir=game_dir, natives_dir=natives_dir,
                    classpath=path)
    features = dict(features or {})

    jvm = vanilla.expand(client.jvm_args, features=features, values=filled)
    if client.logging_argument:
        jvm.insert(0, client.logging_argument)
    if not any(flag in ("-cp", "-classpath") for flag in jvm):
        raise DependencyError(
            f"{client.mc}: the version's own JVM arguments carry no -cp, so the command would "
            "start a client with no classpath"
        )
    if loader is not None:
        jvm.extend(vanilla.expand(loader.jvm_args, features=features, values=filled))
    jvm.extend(extra_jvm)

    game = vanilla.expand(client.game_args, features=features, values=filled)
    if loader is not None:
        game.extend(vanilla.expand(loader.game_args, features=features, values=filled))
    game.extend(extra_game)

    main_class = loader.main_class if loader is not None else client.main_class
    log(f"launch: mainClass {main_class}; {len(path)} classpath entr(y|ies); "
        f"{len(jvm)} JVM argument(s), {len(game)} game argument(s)")
    return [str(java), *jvm, main_class, *game], filled


def launch(command, *, cwd, log_path, log=print):
    """Start the client in ``cwd``, its output appended to ``log_path``.

    The child is its own session on POSIX so the watchdog's SIGTERM/SIGKILL reach
    the client and anything it forked, and not this harness. ``JAVA_HOME`` is
    exported to point at the JDK the client runs on, because a mod (or a
    patched game) that shells out to ``java`` should get the same one.
    """
    cwd = Path(cwd)
    log_path = Path(log_path)
    log_path.parent.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ)
    env["JAVA_HOME"] = str(Path(command[0]).parent.parent)
    handle = open(log_path, "ab", buffering=0)
    kwargs = {"start_new_session": True} if os.name == "posix" else {}
    proc = subprocess.Popen(command, cwd=str(cwd), env=env, stdin=subprocess.DEVNULL,
                            stdout=handle, stderr=subprocess.STDOUT, **kwargs)
    log(f"launch: {' '.join(command)}")
    log(f"launch: pid {proc.pid}, cwd {paths.display(cwd)}, output {paths.display(log_path)}")
    return proc
