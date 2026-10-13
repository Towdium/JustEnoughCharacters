"""Choosing exactly one file for a version description.

Both API clients hand their candidates here so the "which file does
``describe.version`` mean" rule lives in one place and can be tested without a
network. The description may be a version string (``19.37.0.362``), a file name
(``jei-1.21.1-neoforge-19.37.0.362.jar``), a glob (``jei-1.21.1-*.jar``) or a
prefix; an empty description means "the newest candidate of this game version and
loader".

The rule is deliberately strict: no single clear winner is an error that lists
the candidates, never a guess. A wrong pick would silently test the wrong mod
version, which is exactly the failure mode a compatibility harness must not have.
"""

from __future__ import annotations

from .errors import DependencyError
from .util import fold, glob_match, token_set

LIST_LIMIT = 20


def score(needle, fields):
    """Similarity of one description against one candidate's ordered fields.

    Earlier fields carry more weight; a whole-field match beats a token match
    beats a glob/prefix match beats a bare substring.
    """
    wanted = fold(needle)
    if not wanted:
        return 0
    best = 0
    for index, field in enumerate(fields):
        # fields are (label, text) pairs; accept a bare string too
        text = field[1] if isinstance(field, (tuple, list)) and len(field) > 1 else field
        value = fold(text)
        if not value:
            continue
        weight = max(1, 10 - index) * 10
        if value == wanted:
            points = weight + 5
        elif wanted in token_set(value):
            points = weight + 3
        elif glob_match(value, wanted) or value.startswith(wanted):
            points = weight + 2
        elif wanted in value:
            points = weight + 1
        else:
            points = 0
        best = max(best, points)
    return best


def pick(needle, candidates, *, what, source, description):
    """Return the single candidate a description selects, or raise DependencyError."""
    if not candidates:
        raise DependencyError(
            f"{what}: {source} returned no file for {description!r}; "
            "check the game version and loader in the env"
        )
    if not fold(needle):
        newest = sorted(candidates, key=lambda c: c.newest_key, reverse=True)
        release = [c for c in newest if c.is_release]
        chosen = (release or newest)[0]
        return chosen
    scored = [(score(needle, c.match_fields), c) for c in candidates]
    scored = [(s, c) for s, c in scored if s > 0]
    if not scored:
        raise DependencyError(
            f"{what}: no {source} file of {description!r} matches the requested version {needle!r}.\n"
            + render_candidates(candidates, what=what, source=source)
        )
    best = max(s for s, _ in scored)
    top = [c for s, c in scored if s == best]
    if len(top) == 1:
        return top[0]
    if len({c.label for c in top}) == 1:
        return sorted(top, key=lambda c: c.newest_key, reverse=True)[0]
    raise DependencyError(
        f"{what}: the version description {needle!r} is ambiguous ({len(top)} matches).\n"
        + render_candidates(top, what=what, source=source)
    )


def render_candidates(candidates, *, what, source, limit=LIST_LIMIT):
    """The candidate list an error carries, so the fix needs no extra round trip."""
    lines = [f"{what}: candidates from {source} (newest first, {len(candidates)} total):"]
    ordered = sorted(candidates, key=lambda c: c.newest_key, reverse=True)
    for candidate in ordered[:limit]:
        lines.append("  " + candidate.describe())
    if len(ordered) > limit:
        lines.append(f"  ... {len(ordered) - limit} more (use --refresh to re-query)")
    return "\n".join(lines)
