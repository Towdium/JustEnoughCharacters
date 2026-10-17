"""A launch as a row's own build publishes it, and the argv it composes."""

from __future__ import annotations

import os
from pathlib import Path

from ..errors import ConfigError
from ..util import load_json

SCHEMA = 1
PROPERTY_PREFIX = "jech.uitest."


class Description:
    """One launch: where it runs, what it runs, and with which arguments."""

    def __init__(self, *, java_major=None, main_class=None, jvm_args=(), program_args=(),
                 classpath=(), cwd=None, files=None, row=None, task=None, environment=None):
        self.java_major = int(java_major) if java_major is not None else None
        self.main_class = main_class
        self.jvm_args = [str(arg) for arg in jvm_args]
        self.program_args = [str(arg) for arg in program_args]
        self.classpath = [Path(entry) for entry in classpath]
        self.cwd = Path(cwd) if cwd is not None else None
        self.files = dict(files or {})
        self.row = row
        self.task = task
        self.environment = {str(key): str(value) for key, value in (environment or {}).items()}

    @classmethod
    def from_json(cls, data, *, what="launch description"):
        if not isinstance(data, dict):
            raise ConfigError(f"{what}: expected a json object, got {type(data).__name__}")
        schema = data.get("schema")
        if schema is not None and schema != SCHEMA:
            raise ConfigError(f"{what}: schema {schema}, this jt reads schema {SCHEMA}")
        main_class = data.get("mainClass")
        if not main_class:
            raise ConfigError(f"{what}: names no mainClass")
        classpath = data.get("classpath")
        if not isinstance(classpath, list) or not classpath:
            raise ConfigError(f"{what}: names no classpath")
        return cls(
            java_major=data.get("javaMajor"),
            main_class=main_class,
            jvm_args=data.get("jvmArgs") or [],
            program_args=data.get("programArgs") or [],
            classpath=classpath,
            cwd=data.get("cwd"),
            files=data.get("files") or {},
            row=data.get("row"),
            task=data.get("task"),
            environment=data.get("environment") or {},
        )

    @classmethod
    def load(cls, path, *, what=None):
        path = Path(path)
        data = load_json(path, what=what or f"launch description {path.name}")
        return cls.from_json(data, what=what or str(path))

    def to_json(self):
        return {
            "schema": SCHEMA,
            "javaMajor": self.java_major,
            "mainClass": self.main_class,
            "jvmArgs": list(self.jvm_args),
            "programArgs": list(self.program_args),
            "classpath": [str(entry) for entry in self.classpath],
            "cwd": str(self.cwd) if self.cwd is not None else None,
            "files": dict(self.files),
            "row": self.row,
            "task": self.task,
            "environment": dict(self.environment),
        }

    def compose(self, *, java, extra_jvm=(), separator=os.pathsep):
        """The argv that starts this launch (``java`` is the client's JDK).

        An ``@argfile`` stays an argument: the JVM expands one that appears
        before the main class.
        """
        if not self.main_class:
            raise ConfigError("the launch description names no main class")
        if not self.classpath:
            raise ConfigError("the launch description names no classpath")
        argv = [str(java)]
        argv.extend(self.jvm_args)
        argv.extend(str(arg) for arg in extra_jvm)
        argv.extend(["-cp", separator.join(str(entry) for entry in self.classpath)])
        argv.append(self.main_class)
        argv.extend(self.program_args)
        return argv

    def without_properties(self, prefix=PROPERTY_PREFIX):
        """The same description minus JVM arguments carrying ``-D<prefix>…``."""
        kept = [arg for arg in self.jvm_args if not arg.startswith(f"-D{prefix}")]
        return Description(
            java_major=self.java_major, main_class=self.main_class, jvm_args=kept,
            program_args=self.program_args, classpath=self.classpath, cwd=self.cwd,
            files=self.files, row=self.row, task=self.task, environment=self.environment,
        )

    def describe(self):
        return (f"{self.row or '?'} {self.task or '?'}: {self.main_class}, "
                f"{len(self.classpath)} classpath entr(y|ies), {len(self.jvm_args)} JVM "
                f"argument(s), {len(self.program_args)} program argument(s)")


def split_quoted(line):
    """The arguments of one argfile line: whitespace-separated, quotes group."""
    tokens = []
    current = []
    quote = None
    started = False
    for char in line:
        if quote is not None:
            if char == quote:
                quote = None
            else:
                current.append(char)
        elif char in "\"'":
            quote = char
            started = True
        elif char.isspace():
            if started:
                tokens.append("".join(current))
                current = []
                started = False
        else:
            current.append(char)
            started = True
    if started:
        tokens.append("".join(current))
    return tokens


def argfile_tokens(text):
    """Every argument in an ``@argfile``: ``#`` lines are comments."""
    tokens = []
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        tokens.extend(split_quoted(stripped))
    return tokens


def read_argfile(path):
    """The arguments one ``@argfile`` names."""
    return argfile_tokens(Path(path).read_text(encoding="utf-8", errors="replace"))


def drop_property_lines(text, prefix=PROPERTY_PREFIX):
    """``text`` without the lines that are exactly one ``-D<prefix>…`` argument."""
    kept = []
    for line in text.splitlines():
        tokens = split_quoted(line.strip())
        if tokens and all(token.startswith(f"-D{prefix}") for token in tokens):
            continue
        kept.append(line)
    return "\n".join(kept) + ("\n" if kept else "")


def rewrite_paths(text, mapping):
    """``text`` with every path in ``mapping`` replaced by the path it maps to."""
    for source in sorted(mapping, key=len, reverse=True):
        text = text.replace(source, str(mapping[source]))
    return text
