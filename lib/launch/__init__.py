"""Assembling and launching a real client, with no Gradle involved.

The three layers an env's configuration is turned into:

``vanilla``
    one Minecraft version's own files — ``client.jar``, libraries, natives,
    assets, and the client's own ``mainClass``/arguments — read from Mojang's
    metadata and cached under ``cache/mc/<mc>``.
``loader``
    the mod loader's install on top of it (a launch profile plus its own
    libraries), cached under ``cache/loader/<type>-<version>-<mc>``. Fabric is
    fully headless; the Forge family and 1.12.2's launchwrapper are later waves.
``assemble`` / ``launch``
    the run's own game directory (its ``mods/``, its ``options.txt``) and the
    JVM command line that starts the client inside it, tracked by the pid of the
    process this harness itself started rather than by a launcher's tree.
``forgefamily`` / ``description``
    the Forge family's *development* launch: the row's own build publishes the
    description (entry class, module path, legacy classpath, mixin properties,
    harness mod folder), ``forgefamily`` imports it into the cache, and
    ``description`` composes argv from it.

The vanilla/loader split describes a production launch: no Gradle project, no
run task, production jars. A Forge-family row is a development launch and gets
its whole description from its build instead of deriving one here.
"""
