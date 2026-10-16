"""The test selection grammar.

Same shape as the CloudLib harness's ``ScenarioSelection`` (see
``Documents/CloudLib-InworldUI/docs/kb-uitest/01-engine-sessions.md``): a first
expression that includes and a second that subtracts. Terms inside one
expression are OR-ed.

Grammar, per comma-separated term::

    all            (also ``*``)      every target
    <name>                           exact name match (never a substring match)
    tag:<tag>                        exact tag membership
    regex:<pattern>                  ``re.search`` over the target name

An unknown ``foo:bar`` term is treated as a plain name and therefore matches
nothing, which keeps the grammar closed instead of silently matching by prefix.

An empty selection matches nothing, and that is a fact the caller must surface:
``Selection.explain()`` returns a sentence for the report and
``runner`` fails such a run instead of passing it quietly.
"""

from __future__ import annotations

import re

from .errors import UsageError

ALL_TOKENS = ("all", "*")


class Selection:
    """An include expression plus an optional exclude expression."""

    def __init__(self, include=(), exclude=(), *, expression="", exclude_expression=""):
        self.include_terms = list(include)
        self.exclude_terms = list(exclude)
        self.expression = expression
        self.exclude_expression = exclude_expression

    @classmethod
    def parse(cls, expression, exclude=None):
        include_expr = "" if expression is None else str(expression)
        exclude_expr = "" if exclude is None else str(exclude)
        return cls(
            include=split_terms(include_expr),
            exclude=split_terms(exclude_expr),
            expression=include_expr,
            exclude_expression=exclude_expr,
        )

    @property
    def is_empty(self):
        """True when the expression literally contains no term."""
        return not self.include_terms

    def matches(self, name, tags=()):
        tag_set = {str(t) for t in tags or ()}
        included = any(term_matches(term, name, tag_set) for term in self.include_terms)
        if not included:
            return False
        return not any(term_matches(term, name, tag_set) for term in self.exclude_terms)

    def select(self, items, *, name_of=str, tags_of=lambda item: ()):
        return [item for item in items if self.matches(name_of(item), tags_of(item))]

    def describe(self):
        include = self.expression.strip() or "(empty)"
        out = f"include={include!r}"
        if self.exclude_terms or self.exclude_expression.strip():
            out += f" exclude={self.exclude_expression.strip()!r}"
        return out

    def explain(self, matched, total):
        """One line for the report: what the expression selected and why.

        The empty case is spelled out rather than implied — a run that selected
        nothing must never read as a pass.
        """
        if self.is_empty:
            return (
                f"selection {self.describe()} is EMPTY: an empty include expression matches no "
                f"targets, so nothing was run (this is never reported as a pass)"
            )
        if not matched:
            return (
                f"selection {self.describe()} matched 0 of {total} target(s): "
                "nothing was run (this is never reported as a pass)"
            )
        names = ", ".join(sorted(matched))
        return f"selection {self.describe()} matched {len(matched)} of {total} target(s): {names}"


def split_terms(expression):
    terms = []
    for raw in str(expression or "").split(","):
        term = raw.strip()
        if term:
            terms.append(term)
    return terms


def term_matches(term, name, tags):
    folded = term.strip()
    if not folded:
        return False
    lowered = folded.casefold()
    if lowered in ALL_TOKENS:
        return True
    if ":" in folded:
        prefix, _, value = folded.partition(":")
        prefix = prefix.strip().casefold()
        value = value.strip()
        if prefix == "tag":
            if not value:
                raise UsageError("selection term 'tag:' is missing its tag name")
            return value in {t for t in tags}
        if prefix == "regex":
            if not value:
                raise UsageError("selection term 'regex:' is missing its pattern")
            try:
                pattern = re.compile(value)
            except re.error as exc:
                raise UsageError(f"selection term {folded!r} is not a valid regex: {exc}")
            return pattern.search(str(name)) is not None
        return str(name) == folded
    return str(name) == folded
