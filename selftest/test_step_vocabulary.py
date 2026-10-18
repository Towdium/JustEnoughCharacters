"""One step vocabulary: every ``ScenarioSteps`` holder registers the same step names.

``JsonScenarioReader`` asks ``ScenarioSteps.isKnown`` before a scenario runs, so a holder
that lacks a name its siblings carry rejects that scenario at load time (the row cannot run
it) while the sibling row runs it green. The vocabulary is hand-duplicated — a row either
shares one engine copy through ``build.gradle`` or carries its own inline copy — and that is
exactly how ``ime_compose`` / ``ime_commit`` / ``assert_focus_text`` / ``assert_focus_preedit``
and ``click_mouse`` drifted: five holders missed all five names, one group had ``click_mouse``.

The gate reads the names out of every holder in the tree and requires the sets to be equal, so
the next step name added to one copy cannot land unnoticed. No path list: the holders are found
by file name, and generated output (``build/``) is skipped because it holds stale copies of the
same sources.
"""

from __future__ import annotations

import collections
import os
import re
import unittest
from pathlib import Path

from lib import paths

HOLDER_NAME = "ScenarioSteps.java"
REGISTER = re.compile(r'^\s*register\(\s*"([A-Za-z0-9_]+)"\s*,', re.MULTILINE)


def holder_sources():
    """Every checked-in ``ScenarioSteps.java``, as repo-relative paths, in a stable order."""
    root = paths.repo_root()
    holders = []
    for directory, subdirectories, filenames in os.walk(root):
        subdirectories[:] = sorted(name for name in subdirectories
                                   if name != "build" and not name.startswith("."))
        if HOLDER_NAME in filenames:
            holders.append(Path(directory, HOLDER_NAME).relative_to(root))
    return holders


def step_names(holder):
    return REGISTER.findall((paths.repo_root() / holder).read_text(encoding="utf-8"))


def inventory(vocabularies):
    """One line per holder, so a failure states every count it compared."""
    return "; ".join(f"{holder.as_posix()} {len(names)}"
                     for holder, names in vocabularies.items())


class StepVocabularyTest(unittest.TestCase):
    def test_the_holders_are_found_by_name(self):
        """A gate over a glob is only worth its name while the glob finds the holders."""
        holders = holder_sources()
        self.assertGreaterEqual(
            len(holders), 2,
            "a single ScenarioSteps.java makes the equality check vacuous; found: "
            + (", ".join(holder.as_posix() for holder in holders) or "none"),
        )

    def test_every_holder_registers_the_same_step_names(self):
        vocabularies = {holder: step_names(holder) for holder in holder_sources()}
        inventory_line = inventory(vocabularies)
        if not vocabularies:
            self.fail("no ScenarioSteps.java holder is checked in; there is no vocabulary "
                      "to compare")
        modal, votes = collections.Counter(
            tuple(sorted(set(names))) for names in vocabularies.values()).most_common(1)[0]
        reference = sorted(modal)
        offenders = []
        for holder, names in vocabularies.items():
            present = sorted(set(names))
            if present != reference:
                offenders.append(
                    f"{holder.as_posix()} is missing {sorted(set(reference) - set(present))} "
                    f"and carries extra {sorted(set(present) - set(reference))}"
                )
        self.assertEqual(
            offenders, [],
            f"the {len(vocabularies)} step vocabularies disagree; the {len(modal)}-step "
            f"vocabulary shared by {votes} of them is {reference}. Counts: {inventory_line}",
        )

    def test_no_holder_registers_a_step_name_twice(self):
        """A repeat inside one holder silently drops the first handler, set-equality-blind."""
        offenders = []
        for holder, names in {holder: step_names(holder)
                              for holder in holder_sources()}.items():
            offenders.extend(f"{holder.as_posix()} registers {name} {names.count(name)} times"
                             for name in sorted(set(names)) if names.count(name) > 1)
        self.assertEqual(offenders, [], "a step name must be registered once per holder")


if __name__ == "__main__":
    unittest.main()
