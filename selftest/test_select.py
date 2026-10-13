"""The selection grammar."""

from __future__ import annotations

import unittest

from lib.errors import UsageError
from lib.select import Selection

ENVS = [("a-one", ["smoke"]), ("a-two", ["smoke", "forge"]), ("b-one", ["full"])]


class SelectionTest(unittest.TestCase):
    def match(self, expression, exclude=None):
        selection = Selection.parse(expression, exclude)
        return [name for name, tags in ENVS if selection.matches(name, tags)]

    def test_all_token_matches_everything(self):
        self.assertEqual(self.match("all"), ["a-one", "a-two", "b-one"])
        self.assertEqual(self.match("*"), ["a-one", "a-two", "b-one"])

    def test_a_bare_term_is_an_exact_name(self):
        self.assertEqual(self.match("a-one"), ["a-one"])
        self.assertEqual(self.match("a"), [])  # never a substring match

    def test_terms_are_ored(self):
        self.assertEqual(self.match("a-one,b-one"), ["a-one", "b-one"])

    def test_tag_terms(self):
        self.assertEqual(self.match("tag:smoke"), ["a-one", "a-two"])
        self.assertEqual(self.match("tag:full"), ["b-one"])
        self.assertEqual(self.match("tag:nope"), [])

    def test_regex_terms(self):
        self.assertEqual(self.match("regex:^a-"), ["a-one", "a-two"])

    def test_exclude_subtracts(self):
        self.assertEqual(self.match("tag:smoke", "a-one"), ["a-two"])
        self.assertEqual(self.match("all", "tag:smoke"), ["b-one"])

    def test_unknown_prefix_matches_nothing(self):
        self.assertEqual(self.match("group:a-one"), [])

    def test_tag_without_a_value_is_a_usage_error(self):
        with self.assertRaises(UsageError):
            self.match("tag:")

    def test_bad_regex_is_a_usage_error(self):
        with self.assertRaises(UsageError):
            self.match("regex:[")

    def test_empty_expression_matches_nothing_and_says_so(self):
        selection = Selection.parse("")
        self.assertTrue(selection.is_empty)
        self.assertEqual(self.match(""), [])
        explanation = selection.explain([], 3)
        self.assertIn("EMPTY", explanation)
        self.assertIn("nothing was run", explanation)

    def test_blank_terms_do_not_make_a_selection_non_empty(self):
        self.assertTrue(Selection.parse(" , ,").is_empty)

    def test_unmatched_non_empty_expression_says_zero(self):
        explanation = Selection.parse("tag:nope").explain([], 3)
        self.assertIn("matched 0 of 3", explanation)
        self.assertIn("nothing was run", explanation)

    def test_matched_explanation_lists_the_targets(self):
        explanation = Selection.parse("tag:full").explain(["b-one"], 3)
        self.assertIn("matched 1 of 3", explanation)
        self.assertIn("b-one", explanation)

    def test_describe_names_both_expressions(self):
        described = Selection.parse("all", "tag:smoke").describe()
        self.assertIn("include='all'", described)
        self.assertIn("tag:smoke", described)


if __name__ == "__main__":
    unittest.main()
