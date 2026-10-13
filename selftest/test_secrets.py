"""The ``<JT_HOME>/secrets.env`` loader: parsing, precedence and silence."""

from __future__ import annotations

import os
import unittest
from unittest import mock

from lib import cfapi, paths, secrets
from lib.errors import ConfigError, SecretMissingError

from . import support

FAKE_KEY = "unit-test-fake-key-0001"


def write_secrets(home, text, name="secrets.env"):
    path = home / name
    path.write_text(text, encoding="utf-8")
    return path


class ParseTest(unittest.TestCase):
    def test_assignments_comments_and_blanks(self):
        parsed = secrets.parse(
            "# a comment line\n"
            "\n"
            f"CF_API_KEY={FAKE_KEY}\n"
            "  OTHER = spaced  \n"
            "export THIRD='quoted value'\n"
        )
        self.assertEqual(parsed, {"CF_API_KEY": FAKE_KEY, "OTHER": "spaced",
                                  "THIRD": "quoted value"})

    def test_an_inline_comment_is_stripped_from_an_unquoted_value(self):
        self.assertEqual(secrets.parse("A=value # not part of it\n"), {"A": "value"})

    def test_a_hash_inside_a_quoted_value_survives(self):
        self.assertEqual(secrets.parse('A="v # kept"\n'), {"A": "v # kept"})

    def test_a_malformed_line_is_an_error_that_names_the_line_not_its_text(self):
        with self.assertRaises(ConfigError) as caught:
            secrets.parse("CF_API_KEY=%s\nnonsense line\n" % FAKE_KEY)
        message = str(caught.exception)
        self.assertIn("line 2", message)
        self.assertNotIn(FAKE_KEY, message)


class LoadTest(unittest.TestCase):
    def test_a_missing_file_is_not_an_error(self):
        """The CI layout: no secrets file, the variable comes from the runner."""
        with support.temp_home() as home:
            environ = {}
            self.assertEqual(secrets.load(environ=environ), [])
            self.assertEqual(environ, {})
            self.assertFalse((home / "secrets.env").exists())

    def test_the_file_fills_unset_variables_and_reports_only_their_names(self):
        with support.temp_home() as home:
            write_secrets(home, "# the machine-local key\nCF_API_KEY=%s\n" % FAKE_KEY)
            environ = {}
            self.assertEqual(secrets.load(environ=environ), ["CF_API_KEY"])
            self.assertEqual(environ["CF_API_KEY"], FAKE_KEY)

    def test_the_environment_wins_over_the_file(self):
        with support.temp_home() as home:
            write_secrets(home, "CF_API_KEY=%s\n" % FAKE_KEY)
            environ = {"CF_API_KEY": "from-the-environment"}
            self.assertEqual(secrets.load(environ=environ), [])
            self.assertEqual(environ["CF_API_KEY"], "from-the-environment")

    def test_an_unreadable_file_is_reported_clearly(self):
        with support.temp_home() as home:
            directory = home / "secrets.env"
            directory.mkdir()
            with self.assertRaises(ConfigError) as caught:
                secrets.load(environ={})
        self.assertIn("secrets.env", str(caught.exception))

    def test_get_prefers_the_environment(self):
        with support.temp_home() as home:
            write_secrets(home, "CF_API_KEY=%s\n" % FAKE_KEY)
            self.assertEqual(secrets.get("CF_API_KEY", environ={"CF_API_KEY": "env-key"}), "env-key")

    def test_get_reads_the_file_when_the_variable_is_unset(self):
        with support.temp_home() as home:
            write_secrets(home, "CF_API_KEY=%s\n" % FAKE_KEY)
            environ = {}
            self.assertEqual(secrets.get("CF_API_KEY", environ=environ), FAKE_KEY)
            self.assertEqual(environ["CF_API_KEY"], FAKE_KEY)

    def test_get_names_the_file_and_the_variable_but_no_value(self):
        with support.temp_home():
            environ = {}
            with self.assertRaises(SecretMissingError) as caught:
                secrets.get("CF_API_KEY", environ=environ)
            message = str(caught.exception)
        self.assertIn("CF_API_KEY", message)
        self.assertIn("secrets.env", message)

    def test_the_curseforge_client_uses_the_file(self):
        with support.temp_home() as home:
            write_secrets(home, "CF_API_KEY=%s\n" % FAKE_KEY)
            with mock.patch.dict(os.environ, {"JT_HOME": str(home)}, clear=True):
                client = cfapi.CurseForgeClient.from_env()
            self.assertEqual(client._headers(), {"x-api-key": FAKE_KEY})
            self.assertNotIn(FAKE_KEY, repr(client))

    def test_the_secrets_path_is_the_one_the_home_defines(self):
        with support.temp_home() as home:
            self.assertEqual(secrets.load(path=None, environ={}), [])
            self.assertEqual(paths.secrets_path(), home / paths.SECRETS_FILENAME)


if __name__ == "__main__":
    unittest.main()
