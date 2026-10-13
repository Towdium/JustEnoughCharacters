"""Reading machine-local secrets from ``<JT_HOME>/secrets.env``.

The contract (also stated in the root README): that file holds ``CF_API_KEY``
and nothing else, is never committed, and in CI does not exist at all — the
runner exports a real environment variable of the same name instead.

Loading is lazy and additive: a variable that is already set in the environment
always wins, and only names that were actually filled in are reported. A value
read from the file is never printed, logged, cached or echoed in an error
message; a malformed file is reported by line number only.
"""

from __future__ import annotations

import os
import re
from pathlib import Path

from . import paths
from .errors import ConfigError, SecretMissingError

ASSIGNMENT_RE = re.compile(r"^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$")
_QUOTES = ("'", '"')


def parse(text):
    """Parse a tiny dotenv body into a dict; ``#`` comments and blanks are skipped.

    Quoted values keep their inner text verbatim; an unquoted value ends at a
    `` #`` comment marker. A line that is neither blank, a comment nor an
    assignment raises a ``ConfigError`` naming the line number — never its text,
    which could be the secret itself.
    """
    values = {}
    for number, raw in enumerate(str(text).splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        match = ASSIGNMENT_RE.match(line)
        if match is None:
            raise ConfigError(
                f"{paths.SECRETS_FILENAME}: line {number} is not a KEY=VALUE assignment "
                "(comment lines start with '#')"
            )
        key, value = match.group(1), match.group(2).strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in _QUOTES:
            value = value[1:-1]
        else:
            value = value.split(" #", 1)[0].strip()
        values[key] = value
    return values


def load(path=None, *, environ=None):
    """Fill still-unset variables from ``secrets.env``; returns the names filled in.

    A missing file is not an error — that is the CI layout. An unreadable or
    malformed file is, and the error names the file and the line, not the value.
    """
    path = Path(path) if path is not None else paths.secrets_path()
    environ = os.environ if environ is None else environ
    try:
        text = path.read_text(encoding="utf-8")
    except FileNotFoundError:
        return []
    except OSError as exc:
        raise ConfigError(f"{paths.display(path)}: cannot read ({exc})")
    filled = []
    for key, value in parse(text).items():
        if value and not environ.get(key):
            environ[key] = value
            filled.append(key)
    return filled


def get(name, *, environ=None):
    """The value of ``name``: the environment first, then ``<JT_HOME>/secrets.env``.

    The value is returned to the caller only; it is never put into a message.
    """
    environ = os.environ if environ is None else environ
    value = environ.get(name)
    if value and value.strip():
        return value.strip()
    load(environ=environ)
    value = environ.get(name)
    if value and value.strip():
        return value.strip()
    raise SecretMissingError(
        f"{name} is not set. Export {name}=... in the environment, or put {name}=... into "
        f"{paths.display(paths.secrets_path())} (a machine-local file that is never committed; "
        "CI injects the variable instead). jt never prints, logs, caches or accepts a value "
        "on the command line."
    )
