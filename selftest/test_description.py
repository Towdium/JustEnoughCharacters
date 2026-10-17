"""The launch description: argv composition, argfile rewriting, the cache key."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from lib.errors import ConfigError
from lib.launch import description, forgefamily


def description_of(**overrides):
    data = {
        "java_major": 17,
        "main_class": "net.neoforged.devlaunch.Main",
        "jvm_args": ["-Dx=1"],
        "program_args": ["@/row/build/moddev/clientRunProgramArgs.txt"],
        "classpath": ["/row/build/classes/java/main", "/cache/abc.jar"],
    }
    data.update(overrides)
    return description.Description(**data)


class ComposeTest(unittest.TestCase):
    def test_argv_is_java_then_jvm_then_classpath_then_main_then_program(self):
        argv = description_of().compose(java="/jdk/bin/java", separator=":")
        self.assertEqual(argv, [
            "/jdk/bin/java",
            "-Dx=1",
            "-cp",
            "/row/build/classes/java/main:/cache/abc.jar",
            "net.neoforged.devlaunch.Main",
            "@/row/build/moddev/clientRunProgramArgs.txt",
        ])

    def test_extra_jvm_arguments_ride_with_the_descriptions_own(self):
        argv = description_of().compose(
            java="/jdk/bin/java", extra_jvm=["-Djech.uitest.run=tag:smoke"], separator=":")
        self.assertEqual(argv[1:4], ["-Dx=1", "-Djech.uitest.run=tag:smoke", "-cp"])

    def test_a_description_without_a_main_class_is_refused(self):
        with self.assertRaises(ConfigError):
            description_of(main_class=None).compose(java="/jdk/bin/java")

    def test_a_description_without_a_classpath_is_refused(self):
        with self.assertRaises(ConfigError):
            description_of(classpath=[]).compose(java="/jdk/bin/java")

    def test_json_round_trip_keeps_every_field(self):
        original = description_of(files={"log4j": "/cache/files/log4j2.xml"}, row="uitest/x",
                                 task="runClient", cwd="/row")
        restored = description.Description.from_json(
            original.to_json(), what="unit")
        self.assertEqual(restored.main_class, original.main_class)
        self.assertEqual(restored.jvm_args, original.jvm_args)
        self.assertEqual(restored.program_args, original.program_args)
        self.assertEqual([str(entry) for entry in restored.classpath],
                         [str(entry) for entry in original.classpath])
        self.assertEqual(restored.files, original.files)
        self.assertEqual(restored.java_major, 17)

    def test_a_json_description_without_a_classpath_is_refused(self):
        data = description_of().to_json()
        data["classpath"] = []
        with self.assertRaises(ConfigError):
            description.Description.from_json(data, what="unit")

    def test_jech_uitest_properties_can_be_dropped(self):
        d = description_of(jvm_args=["-Da=1", "-Djech.uitest.run=tag:smoke", "-Db=2"])
        self.assertEqual(d.without_properties().jvm_args, ["-Da=1", "-Db=2"])


class ArgFileTest(unittest.TestCase):
    TEXT = "\n".join([
        "# Main Class",
        "cpw.mods.bootstraplauncher.BootstrapLauncher",
        "",
        "--add-opens",
        "java.base/java.util.jar=cpw.mods.securejarhandler",
        '-Dquoted="two words"',
        "-Djech.uitest.run=tag:smoke",
        "-Djech.uitest.out=/runs/x/game/uitest",
        "-p",
        "/cache/a.jar:/cache/b.jar",
        "",
    ])

    def test_comments_quotes_and_one_argument_per_line(self):
        tokens = description.argfile_tokens(self.TEXT)
        self.assertEqual(tokens, [
            "cpw.mods.bootstraplauncher.BootstrapLauncher",
            "--add-opens",
            "java.base/java.util.jar=cpw.mods.securejarhandler",
            "-Dquoted=two words",
            "-Djech.uitest.run=tag:smoke",
            "-Djech.uitest.out=/runs/x/game/uitest",
            "-p",
            "/cache/a.jar:/cache/b.jar",
        ])

    def test_property_lines_are_dropped_and_the_rest_kept(self):
        text = description.drop_property_lines(self.TEXT)
        self.assertNotIn("jech.uitest", text)
        self.assertIn("# Main Class", text)
        self.assertIn("/cache/a.jar:/cache/b.jar", text)

    def test_paths_are_rewritten_longest_first(self):
        mapping = {"/root/a": "/cache/a", "/root/a-b": "/cache/ab"}
        self.assertEqual(description.rewrite_paths("/root/a-b:/root/a", mapping),
                         "/cache/ab:/cache/a")


class CacheKeyTest(unittest.TestCase):
    def key(self, **overrides):
        inputs = {
            "row": "uitest/mv-1.20.1-forge",
            "build_files": {"build.gradle": "a" * 40},
            "harness_digest": "b" * 40,
            "jar_sha1s": {"jech": "c" * 40, "jei": "d" * 40},
            "loader": "forge-1.20.1-47.0.35-1.20.1",
            "script_sha1": "e" * 40,
        }
        inputs.update(overrides)
        return forgefamily.key_of(**inputs)

    def test_the_same_inputs_produce_the_same_digest(self):
        self.assertEqual(forgefamily.key_digest(self.key()), forgefamily.key_digest(self.key()))

    def test_jar_order_does_not_change_the_digest(self):
        other = self.key(jar_sha1s={"jei": "d" * 40, "jech": "c" * 40})
        self.assertEqual(forgefamily.key_digest(self.key()), forgefamily.key_digest(other))

    def test_a_changed_jar_invalidates_it(self):
        other = self.key(jar_sha1s={"jech": "f" * 40, "jei": "d" * 40})
        self.assertNotEqual(forgefamily.key_digest(self.key()), forgefamily.key_digest(other))

    def test_changed_harness_bytes_invalidate_it(self):
        other = self.key(harness_digest="0" * 40)
        self.assertNotEqual(forgefamily.key_digest(self.key()), forgefamily.key_digest(other))

    def test_a_changed_build_file_or_loader_or_script_invalidates_it(self):
        base = forgefamily.key_digest(self.key())
        for overrides in ({"build_files": {"build.gradle": "1" * 40}},
                          {"loader": "forge-1.20.1-47.0.36-1.20.1"},
                          {"script_sha1": "2" * 40}):
            self.assertNotEqual(base, forgefamily.key_digest(self.key(**overrides)))

    def test_tree_digest_follows_content(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "a.txt").write_text("one", encoding="utf-8")
            first = forgefamily.tree_digest([root])
            (root / "a.txt").write_text("two", encoding="utf-8")
            self.assertNotEqual(first, forgefamily.tree_digest([root]))
            self.assertNotEqual(first, forgefamily.tree_digest([root / "missing"]))


if __name__ == "__main__":
    unittest.main()
