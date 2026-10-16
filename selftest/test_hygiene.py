"""Source hygiene: no absolute paths, no secrets, stdlib only, python 3.9 syntax."""

from __future__ import annotations

import ast
import os
import re
import unittest
from pathlib import Path
from unittest import mock

from lib import cfapi, errors, paths
from lib.errors import SecretMissingError

from . import support

ABSOLUTE_PATH_ALLOWED = {"/usr/libexec/java_home"}

MACHINE_PATH_ROOTS = (
    "/Users/", "/home/", "/tmp/", "/var/", "/private/", "/opt/", "/Applications/", "/Library/",
    "/Volumes/", "/usr/", "/etc/", "/mnt/", "/media/", "/srv/", "/System/", "/root/",
)
WINDOWS_PATH = re.compile(r"^[A-Za-z]:[\\/]")

ALLOWED_IMPORT_ROOTS = {
    "__future__", "argparse", "ast", "collections", "contextlib", "dataclasses", "fnmatch",
    "functools", "hashlib", "io", "itertools", "json", "lib", "math", "os", "pathlib", "re",
    "selftest", "shlex", "shutil", "signal", "socket", "subprocess", "sys", "tempfile", "time",
    "typing", "unittest", "urllib", "xml", "zipfile",
}

SECRET_PATTERNS = (
    r"CF_API_KEY\s*=\s*[A-Za-z0-9]{8,}",
    r"ghp_[A-Za-z0-9]{20,}",
    r"gho_[A-Za-z0-9]{20,}",
    r"sk-[A-Za-z0-9]{16,}",
    r"AKIA[0-9A-Z]{16}",
    r"-----BEGIN [A-Z ]*PRIVATE KEY-----",
)


def source_files():
    root = paths.repo_root()
    return sorted(list((root / "lib").glob("*.py")) + [root / "jt"])


def read(path):
    return path.read_text(encoding="utf-8")


def string_literals(tree):
    for node in ast.walk(tree):
        if isinstance(node, ast.Constant) and isinstance(node.value, str):
            yield node.value, getattr(node, "lineno", 0)


class SourceHygieneTest(unittest.TestCase):
    def test_the_entrypoint_has_a_python3_shebang(self):
        first_line = read(paths.repo_root() / "jt").splitlines()[0]
        self.assertEqual(first_line, "#!/usr/bin/env python3")

    def test_no_unexpected_absolute_path_literals(self):
        offenders = []
        for path in source_files():
            tree = ast.parse(read(path))
            for literal, line in string_literals(tree):
                if any(allowed in literal for allowed in ABSOLUTE_PATH_ALLOWED):
                    continue
                token = literal.split()[0] if literal.split() else ""
                if any(root in literal for root in MACHINE_PATH_ROOTS) or WINDOWS_PATH.match(token):
                    offenders.append(f"{path.name}:{line}: {literal!r}")
        self.assertEqual(
            offenders, [],
            "only the documented JDK helper path may name a machine location "
            f"(allowed: {sorted(ABSOLUTE_PATH_ALLOWED)})",
        )

    def test_the_single_allowed_absolute_path_is_still_there(self):
        """The allowlist is only honest while the literal it excuses exists."""
        found = False
        for path in source_files():
            for literal, _line in string_literals(ast.parse(read(path))):
                if any(allowed in literal for allowed in ABSOLUTE_PATH_ALLOWED):
                    found = True
        self.assertTrue(found, "the java-home helper literal is gone; update the allowlist")

    def test_no_secret_literals(self):
        offenders = []
        for path in source_files():
            text = read(path)
            for pattern in SECRET_PATTERNS:
                for match in re.finditer(pattern, text):
                    offenders.append(f"{path.name}: {match.group(0)[:24]}...")
        self.assertEqual(offenders, [])

    def test_only_stdlib_imports(self):
        offenders = []
        for path in source_files():
            tree = ast.parse(read(path))
            for node in ast.walk(tree):
                if isinstance(node, ast.Import):
                    roots = [alias.name.split(".")[0] for alias in node.names]
                elif isinstance(node, ast.ImportFrom):
                    roots = [(node.module or "").split(".")[0]] if node.level == 0 else ["lib"]
                else:
                    continue
                for root in roots:
                    if root and root not in ALLOWED_IMPORT_ROOTS:
                        offenders.append(f"{path.name}:{node.lineno}: {root}")
        self.assertEqual(offenders, [], "the harness must run on a bare python3")

    def test_sources_parse_as_python_39(self):
        offenders = []
        for path in source_files():
            try:
                ast.parse(read(path), filename=str(path), feature_version=(3, 9))
            except SyntaxError as exc:
                offenders.append(f"{path.name}: {exc}")
        self.assertEqual(offenders, [])

    def test_every_module_documents_itself(self):
        undocumented = [path.name for path in source_files()
                        if path.name != "jt" and not ast.get_docstring(ast.parse(read(path)))]
        self.assertEqual(undocumented, [])


class SecretHandlingTest(unittest.TestCase):
    def test_importing_the_curseforge_client_needs_no_key(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            module = __import__("lib.cfapi", fromlist=["cfapi"])
            self.assertTrue(module.BASE_URL.startswith("https://"))

    def test_from_env_fails_without_a_key_and_names_the_variable(self):
        """An empty JT_HOME, so no real secrets file on this machine can satisfy it."""
        with support.temp_home() as home:
            with mock.patch.dict(os.environ, {"JT_HOME": str(home)}, clear=True):
                with self.assertRaises(SecretMissingError) as caught:
                    cfapi.CurseForgeClient.from_env()
        message = str(caught.exception)
        self.assertIn("CF_API_KEY", message)
        self.assertIn("secrets.env", message)

    def test_an_empty_key_is_also_rejected(self):
        with support.temp_home() as home:
            with mock.patch.dict(os.environ, {"JT_HOME": str(home), "CF_API_KEY": "   "},
                                 clear=True):
                with self.assertRaises(SecretMissingError):
                    cfapi.CurseForgeClient.from_env()

    def test_the_key_never_reaches_a_cache_file_name_or_body(self):
        secret = "SUPER-SECRET-KEY-123"
        with support.temp_home():
            client = cfapi.CurseForgeClient(secret)
            headers = client._headers()
            self.assertEqual(headers, {"x-api-key": secret})
            cache_path = client.http._json_cache_path(
                "https://api.curseforge.com/v1/mods/search", headers)
            self.assertNotIn(secret, cache_path.name)
            client.http._write_cache(cache_path, "https://api.curseforge.com/v1/mods/search",
                                     {"data": []})
            written = cache_path.read_text(encoding="utf-8")
            self.assertNotIn(secret, written)
            self.assertNotIn("x-api-key", written)

    def test_the_key_is_never_rendered_into_a_repr_or_log(self):
        with support.temp_home():
            client = cfapi.CurseForgeClient("SUPER-SECRET-KEY-123")
            self.assertNotIn("SUPER-SECRET-KEY-123", repr(client))
            self.assertNotIn("SUPER-SECRET-KEY-123", str(vars(client).keys()))


class PathsTest(unittest.TestCase):
    def test_the_workspace_data_dir_is_computed_not_literal(self):
        """The chain's second step is derived from this file, never written down."""
        self.assertEqual(paths.repo_data_home(),
                         paths.workspace_root() / "Data" / "jech-compat")
        for literal, _line in string_literals(ast.parse(read(Path(paths.__file__)))):
            self.assertNotIn("Data/jech-compat", literal)

    def test_the_default_home_prefers_the_workspace_data_dir(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            with mock.patch.object(paths, "repo_data_home", return_value=paths.repo_root()):
                self.assertEqual(paths.default_jt_home(), paths.repo_root())

    def test_the_default_home_falls_back_to_the_per_user_dir(self):
        missing = paths.repo_root() / "does-not-exist"
        with mock.patch.dict(os.environ, {}, clear=True):
            with mock.patch.object(paths, "repo_data_home", return_value=missing):
                self.assertEqual(paths.default_jt_home(),
                                 Path(os.path.expanduser("~/.jech-compat")))

    def test_the_home_can_be_relocated(self):
        with support.temp_home() as home:
            self.assertEqual(paths.jt_home(), home)

    def test_an_explicit_home_wins_over_the_workspace_data_dir(self):
        with support.temp_home() as home:
            self.assertNotEqual(paths.jt_home(), paths.repo_data_home())
            self.assertEqual(paths.jt_home(), home)

    def test_the_secrets_file_lives_in_the_home(self):
        with support.temp_home() as home:
            self.assertEqual(paths.secrets_path(), home / "secrets.env")

    def test_the_data_skeleton_is_created_on_demand(self):
        with support.temp_home() as home:
            for path in (paths.cache_dir(), paths.http_cache_dir(), paths.mods_cache_dir(),
                         paths.worlds_cache_dir()):
                self.assertFalse(path.exists())
            paths.ensure_data_dirs()
            for rel in ("cache/http", "cache/mods", "cache/worlds", "runs"):
                self.assertTrue((home / rel).is_dir(), rel)
            self.assertFalse((home / "prism").exists(),
                             "no launcher data root is created any more")

    def test_envs_dir_is_inside_the_harness(self):
        self.assertEqual(paths.envs_dir(), paths.repo_root() / "envs")

    def test_display_prefers_a_relative_rendering(self):
        with support.temp_home() as home:
            self.assertEqual(paths.display(home / "runs" / "x"), "runs/x")

    def test_the_exit_code_contract_is_documented(self):
        self.assertEqual(sorted(errors.EXIT_CODE_MEANING), [0, 1, 2, 3])
        self.assertEqual(errors.EXIT_OK, 0)
        self.assertEqual(errors.EXIT_FAIL, 1)
        self.assertEqual(errors.EXIT_USAGE, 2)
        self.assertEqual(errors.EXIT_WATCHDOG, 3)


if __name__ == "__main__":
    unittest.main()
