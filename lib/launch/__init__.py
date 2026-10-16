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

The point of the split is that a config describes *what the environment is* and
nothing else: no Gradle project, no run task, no development-run workarounds.
A production launch loads production jars, so the flatDir remapping, the LWJGL
force, ``--add-opens`` and the ``pack.mcmeta`` workaround that only exist because
a *dev* run uses different namespaces than production simply do not come up.
"""
