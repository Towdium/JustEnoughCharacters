# Env files

One JSON file per test environment, named after its `name` field (the file stem
becomes the run id). `jt envs` loads them all and `jt resolve`/`jt run` select
from them. `lib/envs.py` is the executable form of this document: it rejects
unknown keys, so a typo fails loudly instead of silently doing nothing.

Every file may carry `_`-prefixed comment keys (`_comment`, ...); `$schema` is
ignored too. Everything else must be listed below.

## The env file

```json
{
  "name": "1.21.1-neoforge",
  "tags": ["1211", "neoforge", "smoke"],
  "mc": "1.21.1",
  "loader": { "type": "neoforge", "version": "21.1.215" },
  "java": { "version": 21 },
  "uitest": { "selection": "tag:smoke,tag:jei", "project": "uitest/1.21.1-neoforge" },
  "jech": {
    "source": "local-build",
    "project": "1.21.1",
    "path": "neoforge/build/libs/jecharacters-1.21.1-neoforge-*.jar",
    "expectModId": "jecharacters"
  },
  "mods": [
    { "name": "jei", "source": "curseforge", "slug": "jei", "describe": { "version": "19.37.0.362" }, "pin": null }
  ],
  "checks": [
    { "type": "log_contains", "value": "Registering JEI pinyin search storage", "name": "jei-storage-registered" },
    { "type": "uitest_report", "name": "uitest-report" },
    { "type": "no_crash_reports", "name": "no-crash-reports" },
    { "type": "exit_code_zero", "name": "exit-code" }
  ],
  "timeout_sec": 600
}
```

### Top level

| key | required | meaning |
| --- | --- | --- |
| `name` | yes | env id, `[A-Za-z0-9._-]+`; also the run-id prefix |
| `tags` | no | list of strings; what `tag:<t>` selections match |
| `mc` | yes | Minecraft version (`1.21.1`), used for every resolution query |
| `loader` | yes | `{type, version}`, see below |
| `java` | yes | `{version}` — the **major** version (`21`), matched against the JDKs installed on the machine |
| `jech` | yes | the mod under test, see below |
| `mods` | yes | list, may be empty |
| `checks` | yes | list, may be empty (but an env with no checks proves nothing) |
| `ready_when` | no | `{"value": "<log line>"}`, the line that marks the game as up, see below |
| `world` | no | `{"name": "<folder>"}`, the world the client is launched into, see below |
| `skip` | no | a reason string: the env is **not executed**, see below |
| `gradle` | no — required only without a standalone `uitest` build | `{task, project_dir, daemon_java}` — a run task in the checkout under test that launches the client; no row declares one today, see below |
| `uitest` | no (every row declares one) | `{selection, project}` — run the **in-client UI test harness** the `project` build carries, with that scenario selection, and judge the run by the harness's own report, see below |
| `timeout_sec` | no | watchdog budget, default 600; the whole run is killed and the run fails with exit code 3 |

### `gradle` — a launch layer no row uses

An env with a `gradle` block is executed by running a Gradle task in the checkout
under test (`./gradlew <task> --no-daemon -PjechRunDir=<game dir>`, plus
`-PjechQuickPlayWorld=<world>` when the env declares a world). It is one of the
two ways into the Gradle backend, and **no row uses it today**: every row names a
standalone harness build instead (`uitest.project`, see below), and the schema
makes the two mutually exclusive (`lib/envs.py:326`). The block is still required
for a row that is neither `skip` nor a standalone harness row, so an env that
names no launch layer at all is a schema error (see the `skip` section) rather
than a run that quietly launches nothing — the Prism instance launcher, the layer
before this one, was removed on 2026-10-14. Resolution, the world seed, the pack,
isolation, the watchdog, `ready_when` and the checks are all
independent of the launch layer, and the report records the run task and directory
it used (`gradle.task`, `gradle.projectDir`). See [README.md](../README.md), "The
`gradle` backend", for the full contract.

| key | required | meaning |
| --- | --- | --- |
| `task` | yes | the Gradle task, spelled from the build root (`:neoforge:runClient`) |
| `project_dir` | no | the checkout to run in, relative to `$JT_JECH_ROOT`; defaults to the env's `jech.project`, so the task always runs where the artifact under test was built |
| `daemon_java` | no | the major version of the JDK that runs **Gradle** (its `JAVA_HOME`), e.g. `21` for a Gradle 8.13 wrapper on a Java 25 machine; absent/`null` keeps the ambient `JAVA_HOME`. This is not the client's JDK: that comes from the checkout's own toolchain. Only a `gradle` row reads this key; a standalone harness row starts its Gradle on the env's `java` version |
| `args` | no | extra arguments appended to the Gradle command line **verbatim**, after the ones `jt` spells itself (`-PjechRunDir`, `-PjechQuickPlayWorld`, `-PjechTest`). For a checkout property that varies per row and that only the row can name — the one property it was written for is `-PdevMod=jei\|hei`, which picks the JEI-line dev mod the `1.12.0` checkout stages, and no row passes args today. A list of plain strings; `jt` never interprets them, and they are recorded in the run report (`gradle.args`) so a reader can reproduce the exact command line |

`env.jech` and each `mods[]` entry take one more optional key:

| key | values | meaning |
| --- | --- | --- |
| `via` | `game-dir` (default) / `dev-classpath` | where the artifact's jar goes. `game-dir` copies it into the game directory's `mods/` — what a launch needs when the run does not already carry the mod. `dev-classpath` stages nothing: the run loads the artifact itself (a standalone harness build from the path `jt` hands it as `-PjechJar`/`-PjeiJar`, a `gradle` row from a dependency its checkout's own run declares). It is still resolved, pinned and reported; staging it as well would load the same mod id twice |

A `via` that contradicts what the run actually provides is not detectable
from the env file — the rows are the written record of the choice (see
the checks in `selftest/test_envs.py`), and a wrong one shows up as a duplicate-mod
error in the run's log rather than as a silent pass. **Every artifact of every row
is `dev-classpath` today**, and for a standalone harness row that is not a choice:
the schema rejects `game-dir` there (`lib/envs.py:332`), because the harness build
loads every jar itself, so a staged copy would double-load. What the choice was
originally made along — which checkout module declares JEI `localRuntime`, which
fabric-api `modApi`, which row passes `-PdevMod` — belongs to the checkout-task
shape the rows have left; the row notes below keep only the parts a run still
depends on.

The one `via` reason that still describes a run's own behaviour is the 1.12.2
line's: a 1.12.2 JEI/HEI jar cannot be dropped into `mods/` as published, because
its access transformer is written in SRG member names and a dev client has
MCP-named members, so the rules never match and JEI dies in its own `init`
(measured — see "The 1.12.2 rows and the gradle launch"). The harness build both
rows run stages that jar into its own game directory with the rules remapped, so
staging the pinned jar as well would load the same mod id twice *and* lose the
remap.

`loader.type` is one of `forge`, `neoforge`, `fabric`, `quilt`.

`loader.version` is the **mrpack dependency string** (`lib/pack.py` writes it into
`modrinth.index.json` verbatim):

| loader | the value | example |
| --- | --- | --- |
| `neoforge` | the bare NeoForge version | `21.1.215`, `21.10.64`, `26.1.2.59-beta` |
| `forge` | `<mc>-<forge version>` | `1.20.1-47.0.35`, `1.12.2-14.23.5.2864` |
| `fabric` | the **fabric-loader** version (not the fabric-api version) | `0.16.14`, `0.19.2` |
| `quilt` | the quilt-loader version | |

### Forge rows and the gradle launch

The four `multiple-version` forge rows were the rows that had **no launch path**.
They were the only rows still launched by Prism — the fabric half of the same
checkout runs its own standalone harness builds — and the Prism instance launcher
was removed on 2026-10-14, which left all four with nothing to launch them until
each row got its own standalone harness build (`uitest/<row>`, an independent
Gradle build in this repository — see `uitest` below). The measurement below is
why they cannot simply move to the checkout's task; it was
taken on 2026-10-14, while the launcher still existed, by wiring the four modules
the same way the fabric ones were (property guards in the client run,
`targets.json` through `processResources`, JEI as a dev-only mod dependency,
`daemon_java: 17`) and then running each module bare, with no harness property, to
separate "the checkout cannot be dev-run" from "the harness wires it wrongly". Two
independent blockers came out, and neither is in the harness:

* **1.18.2 / 1.19.2 / 1.20.1 — a JPMS split package.** The dev run dies before
  Minecraft starts:

  ```
  java.lang.module.ResolutionException: Module generated_<hash> contains package
  me.towdium.jecharacters, module jecharacters exports package
  me.towdium.jecharacters to generated_<hash>
      at cpw.mods.modlauncher.ModuleLayerHandler.buildLayer(ModuleLayerHandler.java:75)
  ```

  Forge's module layer wants one module per package. Here there are two: the
  platform module's own output (its `src/` — the `@Mod` class lives there — plus
  the `:common-main` sources the root `build.gradle` copies into every impl
  module) and the jar loom injects out of the `:common` project (its
  `META-INF/mods.toml` makes the loader treat it as a mod too). Both put classes
  in `me.towdium.jecharacters`, and both are needed at runtime — the injected
  side carries the version-level classes the module calls
  (`JustEnoughCharacters.init`, `PlatformUtils`, `JechSearchStorage`, the JEI
  plugin) — so the split cannot be removed by dropping an env's dependency, and
  the fix would have to change where the checkout puts its classes (upstream's
  design), not what an env declares. Fabric never notices: fabric-loader has no
  module layer, which is why the same duplication is harmless in the four fabric
  siblings.

  This is not the harness's doing. A plain `./gradlew :1.20.1:forge:runClient`
  with no `-PjechRunDir` and no world fails identically (measured 2026-10-14: the
  control run in `Data/jech-compat/mv-gradle-4env-20261014/`), and the same holds
  for 1.18.2 with a different module hash. The rows therefore had no working
  launch of their own: they stayed on the launcher, where they were green, until
  that launcher was removed.

* **1.16.5 — the checkout's own dev artifact is broken.** No module layer here
  (1.16.5 is pre-JPMS), so the client boots and renders the overlay, then dies in

  ```
  java.lang.NoSuchMethodError: 'com.mojang.math.Transformation
  com.mojang.math.Transformation.func_227987_b_()'
      at net.minecraft.core.BlockMath.<clinit>(BlockMath.java:26)
  ```

  In the merged dev jar loom builds for forge 1.16.5-36.2.42, `BlockMath` still
  calls the SRG name, while the `com.mojang.math.Transformation` on the same run
  classpath has only the named members — the artifact is inconsistent with
  itself, and no JECh code is on that path. Deleting the cached 36.2.42
  mappings/merged jars and letting loom rebuild them reproduced it exactly, so it
  is not a half-written cache. 36.2.39's cached artifact does not show it, but
  the shipped jar's own `mods.toml` demands `forge [36.2.41,)`, so pinning the
  checkout back is not an option — the version bump belongs to the fix described
  under the check families below.

  **The published jar is unaffected**: it is built by
  `transformProductionForge`/`remapJar` and is what the instance row staged, and
  that row was green (measured 2026-10-14) before the launcher was removed. What
  is broken is only a development run.

Both blockers belong to the checkout, so the probe's module edits were reverted
(the checkout is as upstream has it, plus the `forge_version` bump) and the rows
were left on the launcher until it was removed. The three rows that still had a
working launch path then declared `skip` — `mv-1.16.5-forge`, `mv-1.18.2-forge`
and `mv-1.20.1-forge`; `mv-1.19.2-forge` already carried one — and their checks
stay written down. They were the last rows to leave that list: what revived them
is the standalone harness build each row now owns (`uitest/<row>`), which closes
both gaps the other way — the checkout's dev run is not the launch path at all,
and JECh rides that build as one jar — so **no env carries `skip` today**. The
recipe below is the checkout-side wiring the *other* route would have needed; it
is recorded as the measurement's other half, and no row uses it — the harness
builds carry their own wiring — so the split package (or the broken dev artifact)
never had to be dealt with at all:

* in each module's client run, guard the two properties this way — loom joins
  `runDir` onto the module directory with `new File(projectDir, runDir)`, so an
  absolute game directory has to travel as `project.relativePath(new File(...))`:

  ```groovy
  def jechRunDir = providers.gradleProperty("jechRunDir")
  def jechQuickPlayWorld = providers.gradleProperty("jechQuickPlayWorld")
  // inside runs { client { ... } }
  if (jechRunDir.isPresent()) { runDir project.relativePath(new File(jechRunDir.get())) }
  if (jechQuickPlayWorld.isPresent()) {
      programArg "--quickPlaySingleplayer"; programArg jechQuickPlayWorld.get()
  }
  ```
* route the generated targets through `processResources` and keep the jar tasks
  from seeing the same path twice. Forge's coremod resolves
  `ASMAPI.loadData('me/towdium/jecharacters/targets.json')` **relative to the
  coremod**, which in a dev run is the module's resource output — the plugin's
  extra output directory only reaches the jar, so the run threw
  `NoSuchFileException` from `CoreMod.initialize` before this was added (measured
  on 1.16.5):

  ```groovy
  processResources { from(tasks.named("generateTargetConfig")) { into "me/towdium/jecharacters" } }
  tasks.named("jar", Jar) { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
  // and the same line inside shadowJar { }
  ```
* add JEI as a *dev-only mod dependency*, not a staged jar: a Forge dev run has no
  runtime remapper of its own, so a production jar in `mods/` would be SRG-named
  inside a named-namespace runtime, while loom's mod dependency remapper is what
  makes a classpath mod work. `modRuntimeOnly("mezz.jei:jei-<mc>-forge:<version>")
  { transitive = false }` in the three 1.18.2+ modules (the maven file is
  byte-identical to the CurseForge pin, same size and sha1), and upstream's own
  `modImplementation "curse.maven:jei-238222:4371666"` in 1.16.5. `transitive =
  false` matters: `jei-<mc>-forge` already carries the `mezz/jei/common` classes,
  and letting its `-common` sibling in produced
  `duplicate input class mezz/jei/common/...` during the run.
* `daemon_java: 17` for all four: the wrapper is Gradle 8.1.1, which cannot start
  on the machine's default JDK. The client's own JDK still comes from the module's
  toolchain (8 for 1.16.5, 17 for the rest).

### The 1.12.2 rows and the gradle launch

Both `1.12.2-*` rows **run today** (revived 2026-10-14, the same day they were
declared `skip`), through the standalone harness build they share,
`uitest/1.12.2-forge` — its own ForgeGradle build in this repository:

```
./gradlew runClient --no-daemon -PjechRunDir=<game dir> \
    -PjechTest=tag:smoke,tag:jei -PjechJar=<jech srg jar> -PjeiJar=<jei|hei jar>
```

The two rows run the same build and differ only in the jar `jt` hands it as
`-PjeiJar` (the JEI-line row gets legacy JEI, the HEI row Had Enough Items). The
build's client run carries the arm64 LWJGL 2 natives substitution the checkout
used to provide, and stages the JEI-line jar into its own game directory with the
access transformer remapped from SRG to MCP member names.

Local-network quirk (measured twice, 2026-10-14): a Gradle invocation in this
line's `1.12.0` checkout — the build `jt` falls back to when the row's
`jech.path` glob finds no jar — asks mcmaven for Mojang's launcher manifest,
whose cache
(`~/.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/launcher_manifest.json`)
has a 1-hour TTL; when it expires and the Java-side fetch times out, every
build dies in `configuring root project '1.12.0'` with
`HTTP connect timed out` — before any task runs. Re-warming that one file with
`curl` from the same piston-meta URL unblocks it (a hosted CI network will not
hit this).

Their history is worth keeping, because every step in it is a fact about this
machine or about 1.12.2 rather than about `jt`. It is the record of the
checkout route the rows have left — the `1.12.0` checkout's own `runClientTests`,
measured end to end with that task and no harness property in play
(`Data/jech-compat/112-gradle-20261014/`, `1.12.0` checkout, client toolchain 8):

* **The bare run did not even resolve.** `./gradlew runClient` in `1.12.0` dies
  in `:runtimeClasspath`: Forge's maven mirror serves
  `ca.weblite:java-objc-bridge:1.0.0`'s POM and its main jar but **404s the
  `natives-osx` classifier jar** the 1.12.2 `classes-macos` variant selects
  (`maven.minecraftforge.net/.../java-objc-bridge-1.0.0-natives-osx.jar` → 404,
  while the same file on `libraries.minecraft.net` → 200, 5629 bytes). Gradle
  pins a module to the first repository that answers with metadata, so the mirror
  that has the POM wins and the one that has the jar is never asked. That is
  repository rot, not a checkout defect; the checkout now declares the repository
  that has the file, first and group-restricted:

  ```groovy
  maven { url = 'https://libraries.minecraft.net/'; content { includeGroup 'ca.weblite' } }
  ```
* **Then the client could not load a window.** The launcher
  (`net.minecraftforge.legacydev.MainClient`, the checkout's own JDK 8) dies in
  Minecraft's initialization:

  ```
  java.lang.UnsatisfiedLinkError: <cache>/natives/liblwjgl.dylib: dlopen(...):
    tried: '...liblwjgl.dylib' (fat file, but missing compatible architecture
    (have 'i386,x86_64', need 'arm64e' or 'arm64e.v1' or 'arm64' or 'arm64'))
      at net.minecraft.client.Minecraft.getSystemTime(Minecraft.java:3159)
  ```

  MC 1.12.2 is a LWJGL **2** title and LWJGL 2's macOS natives exist for
  i386/x86_64 only; this machine is arm64 and every JDK it has is arm64
  (`/usr/libexec/java_home -V`). The dev-run path has nothing to substitute:
  ForgeGradle's slime-launcher extracts the official natives jars it resolved
  (`lwjgl-platform-2.9.2-nightly-20140822-natives-osx.jar`,
  `jinput-platform-2.0.5-natives-osx.jar`,
  `java-objc-bridge-1.0.0-natives-osx.jar`) into its cache and points the
  client's `nativesDirectory` at them. **Solved in the checkout**:
  `1.12.0/build.gradle` carries a *conditional* natives repository (macOS +
  `os.arch == aarch64` only) that supplies arm64 builds of LWJGL 2 / jinput /
  objc-bridge — the same third-party `natives-osx-arm64` builds the retired Prism
  meta pointed at, each verified by sha1 — and substitutes `text2speech`. On any
  other host the declaration is inert, and the shipped jars are byte-identical to
  the unpatched output. The harness build both rows run carries the same
  conditional repository, which is what boots their client today.
* **What made the same row green on the instance backend was the launcher, not
  the checkout.** The `1.12.2-forge-jei-legacy` instance ran on the *same* arm64
  JDK 8 (`instance.cfg`: `JavaPath=…/azul-1.8.0_492/…`,
  `JavaRealArchitecture=aarch64`), but its `natives/liblwjgl.dylib` and
  `openal.dylib` are **arm64** binaries, `libjinput-osx.dylib` is fat
  x86_64+arm64, and its classpath carries `java-objc-bridge-1.1.0-mmachina.1`
  instead of 1.0.0. Those come from the launcher's own meta for
  `org.lwjgl:2.9.4-nightly-20150209`, which declares an extra
  `natives-osx-arm64` classifier with a URL and sha1 pointing at a third-party
  build (`github.com/theofficialgman/lwjgl3-binaries-arm64`, branch
  `lwjgl-2.9.4`) — the same substitution the rows' harness build now carries
  itself (and the checkout's conditional repository too), which is why losing the
  launcher stopped being fatal for these rows.
* **Names were not the blocker here, though — that expectation is settled the
  other way.** `FMLDeobfTweaker.injectIntoClassLoader` registers
  `net.minecraftforge.fml.common.asm.transformers.DeobfuscationTransformer`
  unconditionally ("Deobfuscation transformer, always last",
  `net/minecraftforge/fml/common/launcher/FMLDeobfTweaker.java:36-38`), and in a
  deobfuscated environment its `shouldTransform` still accepts everything outside
  `net.minecraft.*`, `net.minecraftforge.*` and the twelve exempt library
  prefixes (`…/fml/common/asm/transformers/DeobfuscationTransformer.java:46-51,
  78-91`), so a third-party mod class loaded from a production jar is still run
  through `FMLRemappingAdapter`. The probe log's
  `Managed to load a deobfuscated Minecraft name- we are in a deobfuscated
  environment. Skipping runtime deobfuscation` is
  `CoreModManager.handleLaunch`'s own environment probe
  (`…/fml/relauncher/CoreModManager.java:192-215`) and what it skips is the other
  direction. **Measured since**: the harness runs on this line with JEI 4.16.1.301
  loaded (`mods=[FML, forge, jecharacters, jechtest, jei, mcp, minecraft]` in the
  harness's own report), and the row's two transformer markers are in the log.
* **What already works, so a future pass does not redo it.** The checkout's
  coremod *is* discovered in a dev run (`Found and added coremod:
  me.towdium.jecharacters.core.JechCore`, and
  `Found a command line coremod : me.towdium.jecharacters.core.JechCore` — the
  launcher turns the project's `META-INF/MANIFEST.MF` into
  `-Dfml.coreMods.load`), `<game dir>/mods` is scanned, and JECh's coremod ran
  far enough to write `<game dir>/config/JustEnoughCharacters.cfg`
  (`JechCore.injectData` → `JechConfig.init`), which is the row's
  `config-generated` check. The half the probe could not reach — the mod that has
  to load after Minecraft bootstraps — is the half the harness run now covers
  (see the measurements below).
* **Two blockers stood between "the client boots" and "the rows are green".**
  Both were found by running the harness on this line, and both are fixed where
  they belong:
  * **JECh's own coremod NPE'd** on `JechModContainer.getSource()` — a
    command-line coremod has no `coremodLocation` — which is why the harness
    finds its JSON scenarios by scanning the classpath instead of asking the mod
    container. The harness-side half is documented in the KB,
    `Documents/Jech/docs/kb/03-uitest-harness.md`.
  * **The dev run's access transformers are inert.** A 1.12.2 mod's
    `META-INF/*_at.cfg` is written in SRG member names and Forge's
    `AccessTransformer` matches member names literally, while a dev client's
    members are MCP-named — so every rule misses and JEI dies in its own `init`:

    ```
    net.minecraftforge.fml.common.LoaderExceptionModCrash: Caught exception from Just Enough Items (jei)
    Caused by: java.lang.IllegalAccessError: tried to access method
      net.minecraft.client.renderer.texture.TextureMap.initMissingImage()V
      from class mezz.jei.gui.textures.JeiTextureMap
    ```

    **Measured through `jt`**, which is what makes it an env-file fact and not
    only a build one: with the row's `mods[]` entry left at the default
    `via: game-dir`, `jt` staged the pinned jar verbatim and the run died in
    15.9 s with no game session and no report. The harness build both rows run
    stages the JEI-line jar itself instead — its `stageUiTestDevMod` rewrites
    every SRG member name in the jar's AT to the MCP name its own trailing
    comment names, failing the build on a rule it cannot map — which is why both
    rows declare their JEI-line mod `via: dev-classpath` and stage nothing. The
    standing AT story is unchanged:
    ForgeGradle 2.x collected a dependency's ATs and applied them while the
    classes were still SRG-named; ForgeGradle 7's 1.12.2 pipeline has no
    equivalent (`mcmaven --access-transformer` runs on the finished MCP-named
    artifact, where neither spelling matches), and the staging task is what
    supplies it — `stageUiTestDevMod` in the harness build, the checkout's own
    `stageDevMods` in its `runClient`.

The revival's own measurements, all on 2026-10-14:

* `./gradlew runClientTests --no-daemon -PjechTest=all` in `1.12.0` —
  `jechtest-uitest PASS: 5/5 scenarios passed, 24/24 checks passed, 7 captures`,
  `BUILD SUCCESSFUL`.
* `jt run 1.12.2-forge-jei-legacy` — `[PASS] … in 36.0s`, 10/10 checks, the
  harness's own report `5/5 scenarios, 24/24 checks, 7 captures`.
* `jt run 1.12.2-forge-hei` — `[PASS] … in 27.4s`, 10/10 checks, the harness's
  own report `3/3 scenarios, 16/16 checks, 4 captures`, with
  `Registering JEI pinyin search index` present (the HEI-only
  `IAdvancedSearchRegistry` path) and the legacy suffix-tree marker absent — the
  two markers that separate the rows.
* The negative control: replacing the JEI scenario's `candidate_contains: "石"`
  with a word that cannot exist turned the run red **at the jt layer**
  (`uitest-report: the harness reported 'FAIL' … jei-pinyin-search=FAIL (step 14
  assertMatchRecorded …)`, plus `exit-code: exit code 1`), and restoring it turned
  the same row green again. The scenario asserts on recorded engine behaviour,
  not on a screenshot: the harness's own miss detail prints the last five
  `TreeSearcher.search` records, so a red step says what the engine actually did.

Both rows are harness rows, so they declare no `ready_when` and no `world` (see
`uitest`), and their `lang` fixture is the 1.12.2 spelling — see "The preset
`options.txt`", which is where a wrong language code for this line was found.

### `uitest` — a row's own in-client UI test harness

```json
"uitest": { "selection": "tag:smoke,tag:jei", "project": "uitest/1.21.1-neoforge" }
```

An env with a `uitest` block runs the **in-client UI test harness** that block's
`project` carries: a dev-only mod that drives the real client through scripted
scenarios (keyboard and chat input, JEI's own search bar) and writes its own
verdict. Every row names such a build today — a standalone Gradle project under
`uitest/` in this repository, whose run task the runner drives in three stages
(config → build → test; `lib/uitest_pipeline.py`), handing it the jars under test
as `-PjechJar`/`-PjeiJar`. The scenario selection is handed over as the project
property `-PjechTest=<selection>`, the only gate on that wiring, and the run
honours `-PjechRunDir` exactly as any other run of that build does.

What changes for such a row:

* **The client ends itself.** The harness quits the game once the selection is
  done, so the row declares **no `ready_when`**: a stopping rule would end the
  session mid-scenario and leave the run without a report at all.
  `selftest/test_envs.py` checks both halves of the rule: a harness row must
  declare none, and a row that launches a client nothing makes quit must declare
  one.
* **The level is the harness's own.** The harness runner creates and pins its own
  flat level and deletes the stale ones it owns, so the row declares **no
  `world`** either: a quick-play join would race the level the scenarios are
  asserted against. No harness build reads `-PjechQuickPlayWorld` for the same
  reason.
* **The evidence is the report.** The harness writes `report.json`,
  `report.txt`, `junit.xml` and `screenshots/` into `<game dir>/uitest` before it
  quits, and the run is judged by that report through the `uitest_report` check.
  The log checks stay as well: they say the mod loaded, the report says the
  scenarios ran.
* **A failing scenario is red twice over.** The harness exits non-zero when a
  scenario failed, which fails the Gradle build through the harness build's own
  `verifyJechUiTest`, and `uitest_report` fails on the report. The captures to
  look at are under `<game dir>/uitest/screenshots/<scenario>/`.

| key | required | meaning |
| --- | --- | --- |
| `selection` | yes | the harness's scenario selection, passed to Gradle verbatim: `all`, a scenario name, `a,b` (terms are OR-ed), `tag:<tag>`, `group:<group>`, `mod:<mod>`, `regex:<pattern>` — the harness's own `ScenarioSelection` grammar. **Every term carries its own prefix**, because a bare term is a *name* match: `tag:smoke,tag:jei`, never `tag:smoke,jei` (which would silently run the smoke scenarios alone) |
| `project` | no (every row names one) | the standalone harness build to run, relative to the compat-tests repository (`uitest/1.21.1-neoforge`): an independent Gradle project whose own run task launches the client. A block without it must carry a `gradle` block instead, and the two are mutually exclusive (`lib/envs.py:326`) |

The selection names the scenarios the row was measured with instead of saying
`all`, for the same reason a row's `world` is written down: a row asserts what it
was measured with, and a scenario added later cannot silently change that. Every
row runs the harness today; thirteen of them run `tag:smoke,tag:jei` — the
scenarios their own build ships, the JEI ones among them — and three run the
smoke tag alone, each for a reason of its own:

| row | selection | why not the other rows' |
| --- | --- | --- |
| `mv-1.16.5-fabric` | `tag:smoke` | the one row with no target mod: its build carries a single scenario and no JEI reaches the run, so it claims the smoke tag alone |
| `mv-1.16.5-forge` | `tag:smoke` | this line's JECh jar carries no JEI plugin class, and the JEI scenarios focus JEI's search field with a measured mouse click the row has not been measured with |
| `1.12.2-forge-hei` | `tag:smoke` | HEI answers through `IAdvancedSearchRegistry`, not through the legacy suffix-tree replacement the JEI scenarios assert on; this row claims the smoke scenarios it was measured with rather than scenarios it does not exercise |
| every other row | `tag:smoke,tag:jei` | the scenarios its own harness build ships, the JEI ones by tag among them |

The scenarios themselves — and the step vocabulary a scenario is written in —
are documented next to each build's copies of them
(`uitest/<row>/src/main/resources/uitest/scenarios/README.md`, and the KB's
`03-uitest-harness.md` / `04-match-recording.md` for the 1.12.2 line's copy of the
vocabulary).

### `jech` — the mod under test

| key | when | meaning |
| --- | --- | --- |
| `source` | always | `local-build` (a jar in a checkout), or `curseforge` / `modrinth` (a released file) |
| `project` | `local-build`, optional | the checkout to search, see "Locating a local build" |
| `path` | `local-build`, required | glob, relative to the checkout, e.g. `neoforge/build/libs/jecharacters-*.jar`; must not be absolute |
| `expectModId` | `local-build`, required | the mod id the jar must declare (`jecharacters`); a jar that declares something else is an error, not a warning — it means the glob picked the wrong file |
| `slug` | `curseforge`/`modrinth` | the project slug (exact match, not the display name) |
| `describe.version` | `curseforge`/`modrinth` | the version description to pin, see "Version descriptions" |
| `pin` | `curseforge`/`modrinth`, optional | a resolved file; `null` makes `jt resolve` fill it in |

### `mods[]`

| key | required | meaning |
| --- | --- | --- |
| `name` | yes | label, unique within the env; it is not the file name (that comes from the pin) |
| `source` | yes | `curseforge` or `modrinth` |
| `slug` | yes | project slug |
| `describe.version` | yes | the version description to pin |
| `pin` | no | `null` until `jt resolve` fills it in |
| `tags` | no | free-form labels, for the human reading the file |

### `checks[]`

| key | required | meaning |
| --- | --- | --- |
| `type` | yes | one of the types below |
| `value` | for the types marked ✔ | a string, or a list of strings where every entry must hold |
| `name` | no | the check's name in the report; defaults to `type:value`; must be unique in the env |
| `tags` | no | ignored by the evaluator, kept for humans |

Check types:

| type | value | passes when |
| --- | --- | --- |
| `log_contains` | ✔ pattern(s) | the marker(s) appear somewhere in `latest.log` |
| `log_not_contains` | ✔ pattern(s) | none of the markers appears in `latest.log` |
| `log_regex` | ✔ regex(es) | `re.search` matches each pattern (multiline) |
| `file_exists` | ✔ relative path(s) | every path exists **under the run's game directory** (e.g. `config/jecharacters.json`) |
| `no_crash_reports` | — | no new file appeared in `crash-reports/` during the run |
| `exit_code_zero` | — | the game ended healthy: the runner deliberately stopped it once it was ready, or the game itself exited 0 (SKIP when the launcher exited while the client kept running, so the client's own exit code is unobservable from here) |
| `uitest_report` | optional relative path | the in-client UI test harness reported `PASS` in `uitest/report.json` (or the named path) with no scenario failed or errored. The report is written by the harness itself before it quits the client, so a missing, unreadable or unknown-schema report **FAILs**: "the scenarios ran" is what the check is here to establish, and an unevaluated verdict is not a pass. See `uitest` above |

Three rules keep the checks honest:

* A check whose precondition is missing **SKIPs** — it is never rendered as a
  pass. `log_*` checks SKIP when no new game session log appeared; `file_exists`
  SKIPs when no game directory was located. The runner always adds three
  `internal:` checks (`session-started`, `watchdog`, `game-exit`) that FAIL in
  exactly those cases, so a run with missing evidence fails on a named check.
  `uitest_report` is the deliberate exception to the SKIP half: it only SKIPs when
  no game directory was located at all, and otherwise FAILs on a report it
  cannot read, because a missing report *is* the failure it names.
* Only **INFO-level** markers are assertable. A production client logs at INFO
  by default, so DEBUG-level lines (for example the transformer "hit" lines) are
  not in `latest.log` and must not be used in a check until the log4j level is
  raised by the harness.
* A marker that only appears **once a world is loaded** needs `world` (below).
  JEI 19.x runs most of its plugin phases — including `registerAdvancedSearch`,
  which is what logs `Registering JEI pinyin search storage` — at level load; a
  run that stops at the main menu can never satisfy it, no matter how long it
  waits. Declaring `world` moves the client into a level and the marker arrives
  a second or two after the join. Verify every new marker against a real
  `latest.log` (step 4 of "Adding an env"). Measured across the whole matrix
  (2026-10-14): JEI's level-load phase announces itself as `Sending Runtime` in
  `mezz.jei.library.load.PluginCaller` *after* `joined the game` (1.20.1: join
  at log line 105, `Sending Runtime` at 155, JECh's marker at 141), whereas a
  run that stays at the title screen shows only the startup `Sending
  ConfigManager` and never the marker — JEI 15 (1.20.1), 19 (1.21.1), 26
  (1.21.10/26.1.2) all behave this way. A row that runs the in-client harness
  gets that level without declaring `world`: the harness's runner stands up its
  own flat level (every row does, see `uitest` above).

### `skip` — an env that is deliberately not executed

```json
"skip": "JEI registration check needs world load; quickPlay needs 1.20+"
```

`jt run` never launches a skipped env and never renders it as a pass: the run
entry is `status: SKIP`, the report prints `[SKIP] <env> — <reason>`, and the
process exits **1**. "Not verified" is not "verified fine", so hiding a skip
takes an explicit `--exclude tag:skip`, not an inattentive reading of a green
run. (An env that declares `skip` carries a derived `skip` *tag* for that
selection; the tag is never written back into the env file.)

`skip` blocks execution only. `jt envs` still lists the env, `jt resolve` still
pins and downloads it, `jt seed-world` still maintains its world cache, and the
env keeps its `checks` as the written-down statement of what it would assert
(the reason string is what points at the missing capability).

**No env carries `skip` today.** The four `multiple-version` *forge* rows were the
last to leave that list, and each reason had been a launch-layer one:

* the four `multiple-version` *forge* rows had **no launch layer at all**: they
  were the last rows on the instance launcher, removed on 2026-10-14, and that
  checkout's Forge dev runs cannot host the mod under test either ("Forge rows and
  the gradle launch") — each row now runs on its own standalone harness build
  (`uitest/<row>`), which launches it and creates the level it is judged in;
* `mv-1.19.2-forge` was the one whose *marker* looked unreachable rather than whose
  launch was gone: its row needs a loaded level and quick play only exists from
  1.20 on — the harness build creates the level itself
  (`WorldOpenFlows.createFreshLevel`, the row's env comment names it), so the
  marker is reached without quick play.

The two `1.12.2` rows left the list on 2026-10-14, the same day they joined it:
they were skipped because no path booted a 1.12.2 client on this arm64 machine,
and the checkout now boots one itself and runs the harness on it ("The 1.12.2
rows and the gradle launch"). Their checks were never weakened while they were
skipped, so nothing had to be restored but the launch. With the four forge rows
back, that list is empty: `jt envs` prints no `skip:` line for any of the 16 envs,
and every row is executed when it is selected.

The `mv-1.19.2-forge` skip was re-measured on
2026-10-14 rather than inherited: the row was run on the then-live
instance launcher, with a real seed
staged (`saves/jechworld`, 14 files) and launched as
`prismlauncher --launch … --world jechworld`. The client reached
`Sound engine started` and sat at the title screen for three minutes — no
`Loading level`, no `Preparing spawn area`, no `joined the game` anywhere in
`latest.log` — so no level was ever loaded and the marker could not appear.
That was the launcher behaving as designed, not a harness failure: it only appends
the quick-play game argument when the version profile carries the
`feature:is_quick_play_singleplayer` trait, and its metadata has that
trait from 1.20 on (`net.minecraft/1.19.2.json` `+traits`:
`["XR:Initial","FirstThreadOnMacOS"]`; `1.20.1`/`1.21.1` add both quick-play
traits), while a world target has no fallback argument (the server case falls
back to `--server`/`--port`; a world does not). The env was restored to `skip`
with its checks intact after the probe — a verdict the launcher earned, not one
the row is stuck with: the row needs no launcher at all now, because its own
standalone harness build's runner creates the level the marker needs itself
(`WorldOpenFlows.createFreshLevel`, the row's env comment names it). That is how
the row runs and passes today
(`Data/jech-compat/runs/mv-1.19.2-forge-20261017-142743`).

Its fabric sibling sat in the same position — MC 1.19.2 offers no command-line
way to load a level at all — but it never skipped, because it did not have to
claim a level: it asserted what a client that stops at the title screen can show
(the transformer loaded, the config was written, no crash report). Its own move
to a harness build is what changed that: the harness's runner creates the level
in-client, so `mv-1.19.2-fabric` claims the JEI storage marker today, the way its
forge sibling does. Its env comment carries the same reasoning, and
`selftest/test_envs.py` asserts that both rows declare neither `skip` nor
`world`.

## Launching into a world (`world`)

```json
"world": { "name": "jechworld" }
```

An env with `world` is launched into that world by its run: the harness hands the
name over as the project property `-PjechQuickPlayWorld=<name>`, and a `gradle`
row's checkout client run appends `--quickPlaySingleplayer <name>` to the
client's program arguments. The seed for that world is staged as
`saves/<name>/` inside the run's game directory on every run. **No row declares
`world` today**: every row is a harness row whose own runner creates the level the
scenarios are asserted against (see `uitest`), so no run needs a world handed to
it, and no harness build reads the property.

While the instance launcher existed (until 2026-10-14) the world travelled as its
own *join world on launch* option,
`prismlauncher -d <root> --launch <instance> --world <name>` — a first-class
option, not a game-argument passthrough, because the launcher knew which game
argument each Minecraft version wants (`--quickPlaySingleplayer` for 1.20+) and
built it itself. Two launcher-side paths were tried and rejected, recorded here
so nobody re-treads them:

* `prismlauncher --launch <id> -- --quickPlaySingleplayer <world>`: Qt parses
  everything after `--` as a **positional url**, so the trailing arguments became
  *resource imports* to the launcher, not game arguments. Measurement: with
  `-- --quickPlaySingleplayer jechworld` the launcher opened and stayed open, no
  world was joined.
* A per-instance `.minecraft/patches/*.json` version patch appending to
  `arguments.game`: workable in principle, but it only ever duplicated what the
  launcher already did, and a patch file has to be kept in sync per loader. Not
  used.

Which rows carried it while they ran quick-play launches, as measured on
2026-10-14: `1.21.1-fabric`, the two `moderner-26.1.2` rows and
`mv-1.20.1-fabric` — four of the five rows that carried a `world` block, the
fifth being `mv-1.20.1-forge`, skipped then — all ran to `stopReason:
goal-reached` on a real join (`vfyjxf_ joined the game`, then
JECh's `Registering JEI pinyin search storage` a second later). Every row has
since moved to its own harness build and left the list: the level the scenarios
are asserted against is the one the harness's own runner creates (see `uitest`
above), so the marker still arrives — because the level does — for every row,
including the ones no command-line join reaches (`mv-1.19.2-forge` and its fabric
sibling, see the `skip` section above). The `mv-1.16.5` and `mv-1.18.2` rows
never carried one: their markers are emitted at startup (and the 1.16.5/1.18.2
jars carry no JEI plugin at all), so a world would only add a level load to what
they assert.

The game directory is the run's own `runs/<id>/game`, with a seed staged into
`saves/<name>/` there on every run.

One caveat that cost a full watchdog on the way (recorded so the next person
reads it as designed, not as a bug): a run whose client dies *before* the ready
line — a Fabric `Incompatible mod set!` dialog, say — has no stopping rule at
all, because the bounded grace only starts once the ready line is in the log. It
then waits out `timeout_sec` (exit 3) even though the failure is already in the
log. A ready line that lands whether or not the join succeeds is what turns a
*join* failure into a fast `ready-timeout`; a failure earlier than the ready
line is still a watchdog case.

Name restrictions: `world.name` must be a plain folder name
(`[A-Za-z0-9][A-Za-z0-9 ._-]*`, not `.`/`..`, no separators) because it becomes
one directory under `saves/` and one entry inside the pack.

The seed itself is **built by the vanilla dedicated server of the env's own
Minecraft version**, not by a client:

```bash
python3 jt seed-world 1.21.1-neoforge     # build (or verify and reuse) the seed
python3 jt seed-world 1.21.1-neoforge --refresh   # rebuild even a verifying cache
python3 jt seed-world tag:smoke --exclude tag:112
```

* the server jar comes from Mojang's `version_manifest_v2.json` → the version's
  own json → `downloads.server`, is sha1-verified and cached in
  `<JT_HOME>/cache/servers/server-<mc>.jar` (49.2 MB for 1.21.1);
* it runs in a scratch directory with `eula.txt` accepted, `level-type=flat`,
  `level-name=world` and small view/simulation distances, writes `Done (` to its
  log, then stops over its stdin;
* `world/` is copied to `<JT_HOME>/cache/worlds/<mc>-<name>/world` next to a
  `manifest.json` carrying a per-file sha256 list and a digest over it (measured
  for `1.21.1/jechworld`: 13 files, 3.32 MiB).

An env that declares no world is a no-op here ("declares no world, so there is
nothing to seed") and not a failure, so a broad selection works; but a selection
in which *nothing* needs a seed exits 1 and says so, following the same
fail-closed rule as an empty selection.

The cache key is `(mc, world.name)`, so the two 1.21.1 envs share one seed. The
manifest is what makes reuse honest: `jt seed-world` (and `jt run`, which builds
a missing seed itself) re-hashes the tree on every lookup and rebuilds when it
does not match, so a truncated or hand-edited world is never staged. The world
tree's `session.lock` is deliberately excluded from the manifest and from
staging — it is a session artefact that a client rewrites, not part of the seed.

The digest identifies the build that is on disk, not a cross-machine constant:
the world's *shape* is fixed (flat, a pinned seed), but two builds of the same
`(mc, world)` are not byte-identical — `level.dat` carries the generation
timestamps and every region chunk carries a last-update stamp. Nothing depends on
byte equality across machines; the manifest describes the tree that is actually
cached.

`jt run` builds a seed when the cache is missing, verifies it when it is there,
and re-stages it into the run's game directory on every run (a world a previous
run played in is replaced, never merged).

## Launching without a world

Every env is in this position today: no row declares `world`, so no run begins by
joining one — each row's harness build creates the level its scenarios run in and
quits the client when they are done. The preset `options.txt` (below) is still
staged, so the client reaches the menu before the harness drives it, and the
markers that only appear at level load are reached inside the harness's own
level.

## The preset `options.txt`

A fresh game directory has no `options.txt`, and a client with no
`options.txt` shows the **"Welcome to Minecraft!" accessibility onboarding
screen before the title screen** — a modal page with a Continue button that
nothing in a headless run can press, so the run never reaches the menu. `jt`
therefore renders a minimal preset into the pack (`overrides/options.txt`, see
`lib/options.py`) and stages it into the game directory on every run:

```
onboardAccessibility:true
lang:zh_cn
```

Two keys, and only those:

* `onboardAccessibility:true` marks the accessibility onboarding as finished.
  The client shows that screen while the value is false, which is also the state
  of a profile with no `options.txt` at all. (Field `Options.onboardAccessibility`;
  see the Minecraft Wiki's *Title Screen* and *options.txt* pages, and Mojang's
  own reproduction steps on MC-310326, which open the screen by deleting
  `options.txt`.)
* `lang:<code>` starts the client in Simplified Chinese, where `<code>` is the
  **version's own spelling**: `zh_cn` from 1.13 on, `zh_CN` in 1.12.2 (vanilla
  lower-cased its language files in 1.13, and the option names the file
  `assets/minecraft/lang/<code>.lang`). That is a fixture, not a taste in speech:
  JECh is a *pinyin* search mod, a pinyin query only has anything to search once
  the items carry Chinese names (`block.minecraft.stone` is `石头`, which `shitou`
  reaches; in `en_us` it is `Stone`, which no pinyin can), and the in-client
  harness asserts exactly that.

  **A wrong case silently means English, and it was measured the hard way.**
  Nothing normalises the option: `GameSettings.loadOptions` stores it verbatim
  (`if ("lang".equals(s1)) { this.language = s2; }`, `GameSettings.java:944-947`),
  and `LanguageManager.onResourceManagerReload` loads the locale data of exactly
  the code it was given, plus `en_us` (`LanguageManager.java:60-69`). So a 1.12.2
  client started with `zh_cn` finds only `en_us`, comes up with English item
  names, and the pinyin scenario fails on a build that is perfectly correct —
  found when the first `jt`-driven 1.12.2 row was measured, and fixed in
  `lib/options.py` (`LANGUAGE_CODE`, one entry per version that spells it
  differently). `LanguageManager.getCurrentLanguage` *does* fall back to `en_us`
  for the window's own chrome, which is why the mistake is invisible until a
  scenario looks at an item name.

**A `key_*` line must never be preset, and this is not a style rule.** The preset
carries no `version:` line, so the client's `Options.dataFix` reads it as
data-version 0 and runs the *legacy* fixer chain, in which
`OptionsKeyLwjgl3Fix.makeRule` does `Integer.parseInt` on the value of every
`key_*` entry (it exists to convert LWJGL2's numeric key ids). A modern binding
that carries a modifier is written `key.keyboard.f:CONTROL`, which is not a
number, so the fixer throws, `Options.load()` catches it, and **every** option
(including `lang`) stays at its default — measured 2026-10-14:

```
[Render thread/ERROR] [net.minecraft.client.Options/]: Failed to load options
java.lang.NumberFormatException: For input string: "key.keyboard.f:CONTROL"
    at net.minecraft.util.datafix.fixes.OptionsKeyLwjgl3Fix.lambda$makeRule$1(OptionsKeyLwjgl3Fix.java:147)
```

Pinning JEI's search-bar focus key was tried and removed for that reason, and
nothing was lost: a fresh game directory has no override, so the binding the
harness scenario presses is JEI's own registered default
(`key.keyboard.f` + `CONTROL`).
A `version:<current data version>` line first in the file makes the legacy chain
skip entirely and the same file loads cleanly (verified both ways), but that
number is per Minecraft version and has no cheap authoritative source here —
that is the point at which to add one, if a run ever has to survive a *rebound*
key.

Deliberately **not** preset, with the reason: `skipMultiplayerWarning` (the
warning is for joining a *server*; these runs join single-player),
`tutorialStep` (tutorial hints are toasts, not screens, and gate nothing), and
everything else cosmetic — volume, resolution, render distance, GUI scale —
because a preference written down here would silently become part of what a run
measures. A key an older client does not know is ignored when it loads the file,
so one preset serves every version line.

Verified on a freshly materialized 1.21.1 game directory (measured 2026-10-14,
while the instance launcher still existed): with the preset the client
lands on the title screen, without it the onboarding screen comes first.

## When is the game ready? (`ready_when`)

A client launched by a `gradle` row's checkout task has no in-game runner:
nothing in the game makes it quit,
so without a stopping rule a `jt run` can only end by the game being closed by
hand or by the watchdog (exit code 3). `ready_when` gives the run a stopping
rule. **No env declares it today**: every row is a harness row whose own runner
quits the client when the scenario selection is done, so every run ends at the
game's own exit and the key is on no row's path.

```json
"ready_when": { "value": "Sound engine started" }
```

* **The rule.** The run is *ready* once the `ready_when` line is in
  `latest.log` **and** every `log_contains` value of the env is in it too. At
  that moment the runner ends the session's process tree itself and judges the
  run; the report records `stopReason: "goal-reached"` and `readyReached: true`.
* **What the report tells you when it did not hold.** `readyReached` is the whole
  rule, so a `false` on its own does not say which half was missing. The report
  prints both halves separately: `readyLineSeen` (was the ready line in the log?)
  and `missingMarkers` (which `log_contains` values never arrived). On the
  quick-play rows that declared it, the ready line landed early and the JEI
  marker only at world load, so the honest reading of a pre-`world` failure there
  was
  `readyLineSeen: true, missingMarkers: ['Registering JEI pinyin search storage']`
  — the marker was the missing half, not the ready line.
* **Choosing the line.** Pick a stable INFO line vanilla prints exactly once
  when the client has finished coming up (for 1.12.2 and 1.21.1 alike, the
  classic `Sound engine started` / `Setting user:` family is a good start);
  verify it against a real `latest.log` before trusting it. Order matters: since
  every `log_contains` marker must already be there, either pick a ready line
  that comes *after* your markers, or accept that the run waits for the markers.

  For an env with `world`, keep a line that the client prints **whether or not**
  the world joins (the rows that used quick play ran on `Sound engine started`,
  which lands ~4s before the join). A world-only line such as
  `Preparing start region for dimension` reads better but is a trap: if the join
  ever fails, the ready line never lands, the grace never starts, and the run
  hangs into the watchdog (exit 3) instead of failing on the marker. With a line
  that always lands, a failed join becomes a clean `ready-timeout` + FAIL.
* **A marker that never appears.** The ready line starts a bounded grace window;
  if the markers still have not shown up when it expires, the runner stops the
  run anyway and records `stopReason: "ready-timeout"`. The missing
  `log_contains` check then FAILs (exit code 1) instead of the whole run hanging
  into the watchdog — a broken assertion is a failure, not a timeout.
* **The stop settles first.** Reaching the goal does not end the session
  instantly: the runner keeps watching for `SETTLE_GRACE_SEC` (3s) before it
  kills the process tree, because a client keeps writing for a moment after the
  ready line. Measured on `mv-1.16.5-forge`: FML writes its mod-loading crash
  report (`crash-<stamp>-fml.txt`) and logs `Crash report saved to …` 0.37s
  *after* `Sound engine started`. Killing at the ready line raced that write —
  the same env PASSed once and FAILed once on identical inputs, because one run
  died before FML flushed the report the `no_crash_reports` check reads. The
  settle makes that verdict repeatable (both re-runs FAIL on the same check, with
  the crash report named in the run); it is skipped when the game has already
  exited, and it costs 3s per run. (That row was red at the time for its own
  reason — the loader/metadata mismatch fixed later the same day; see the check
  families below.)
* **`exit_code_zero` with a stopping rule.** When the runner ended the session
  itself, `exit_code_zero` PASSes whatever code the killed process reported (the
  code is an artefact of the harness). A natural exit 0 also passes; a natural
  exit before the ready line, or a non-zero one, FAILs.
* **A launcher that exits before its client.** A Gradle invocation is not
  necessarily the client's parent: it can hand the client to another process and
  exit while the game keeps running. The runner does not read that as "no
  session" — the client is identified by its working directory, so the session is
  tracked as usual and the launcher's own exit code is recorded as unobservable
  (`exit_code_source: detached`), which makes `exit_code_zero` SKIP instead of
  FAIL. A genuinely failed launch still ends as a missing session: a launcher
  that exits with no game process behind it is reported as exactly that.
* **Omitting it.** Without `ready_when` the run behaves as before: it waits for
  the game to exit on its own or for the watchdog. A row that launches a client
  nothing makes quit must declare one: a modded client that reaches the title
  screen never exits on its own, so such a row can only end in a watchdog kill
  (exit 3) — i.e. it is a broken env, not a lenient one. No shipped row is in
  that position today (see above); for one that was, `Sound engine started` is
  the line to reach for — the client prints it in 1.12.2
  through 26.1.2 alike (verified against each client jar), and it lands
  *before* a world join so a failed join still becomes a clean
  `ready-timeout`.
* **The rows that omit it, and must.** An env with a `uitest` block runs its
  harness build, whose own runner quits the client when the
  selection is done. A stopping rule there would end the session mid-scenario and
  leave the run with no report — so such a row declares no `ready_when`, and the
  absence is the correct shape rather than a broken env (every row today; see
  `uitest` above and the assertion in `selftest/test_envs.py`).

## Pins

A pin is written by `jt resolve` and then trusted: `jt resolve` and `jt run` use a
pinned file without querying the API again, which is what makes a run
reproducible and what lets `mv-1.16.5-forge` name a known CurseForge file id
directly.

```json
"pin": {
  "fileId": 4371666,                       // curseforge: required
  "versionId": "abc123",                   // modrinth:  required
  "filename": "jei-1.16.5-7.8.0.1009.jar", // required in practice: it is the cache file name
  "downloadUrl": "https://...",
  "sha1": "cb07340f...", "sha512": "...", "size": 839446
}
```

`filename` is needed as soon as a run has to stage the jar; a pin without one is
reported as an error rather than guessed. `pin: null` (or a missing key) means
"not resolved yet".

## Locating a local build (`jech.source = local-build`)

1. The root is `$JT_JECH_ROOT` when set; otherwise it is the checkout directory
   that contains `compat-tests` (its siblings are the version lines on a
   workstation; in a CI submodule checkout it is the branch checkout itself).
2. `jech.path` is globbed in `<$JT_JECH_ROOT>/<project>` first, then in
   `<$JT_JECH_ROOT>`; the first root that matches wins. That covers both layouts:

   * workstation: `project` names a directory under one shared root
     (`project: "multiple-version"`, `path: "1.20.1/forge/build/libs/...jar"`);
   * CI: the branch checkout root already *is* what `project` names, so the
     second root matches (`project: "moderner"`,
     `path: "neoforge/build/libs/...jar"`).
3. Sources and javadoc jars are never selected. Exactly one jar must remain:
   zero matches and two-or-more matches are both errors that list what was
   searched or matched. Without `project` there is no authority to pick a
   checkout, so every subdirectory is scanned and two matches are ambiguous.

## Version descriptions

`describe.version` is matched by `lib/modmatch.py`, strictly — no winner means an
error listing the candidates, never a guess. Accepted forms:

| form | example |
| --- | --- |
| exact version | `19.37.0.362` |
| exact file name | `jei-1.21.1-neoforge-19.37.0.362.jar` |
| glob | `jei-1.21.1-*.jar` |
| token / prefix | `19.37` |

Whole-field matches beat token matches beat glob/prefix matches beat substrings;
the newest upload breaks ties. An empty description picks the newest release of
that game version + loader.

## Selection grammar

Both `jt resolve` and `jt run` take one expression, optionally subtracted by
`--exclude`. Terms are comma-separated and OR-ed:

| term | selects |
| --- | --- |
| `all`, `*` | everything |
| `<name>` | that exact env name (never a substring) |
| `tag:<tag>` | every env carrying the tag |
| `regex:<pattern>` | `re.search` over the env name |

Examples:

```bash
jt run all --exclude tag:experimental
jt resolve tag:fabric --exclude tag:112
jt run regex:'^mv-1\.1(6|8)\.2-'
jt resolve 1.21.1-neoforge,1.21.1-fabric
```

An empty or unmatched selection is never a pass: `jt run` fails with exit code 1
and says so in the report.

## The shipped matrix

16 envs, one per (version line, loader) pair. Tags: the version line
(`moderner`, `1211`, `mv`, `112`), the loader, and `smoke` on all of them;
`experimental` marks the rows whose upstream version pins are doubtful, and
`legacy` marks the 1.12.2 JEI row.

| env | mc / loader | java | jech checkout | target mods |
| --- | --- | --- | --- | --- |
| `moderner-1.21.10-neoforge` | 1.21.10 / neoforge 21.10.64 | 21 | `moderner` | jei 26.3.0.31 |
| `moderner-1.21.10-fabric` | 1.21.10 / fabric 0.19.2 | 21 | `moderner` (fabric-legacy) | jei 26.3.0.31, fabric-api 0.138.3+1.21.10 |
| `moderner-26.1.2-neoforge` | 26.1.2 / neoforge 26.1.2.59-beta | 25 | `moderner` (neoforge-unobfuscated) | jei 29.16.0.46 |
| `moderner-26.1.2-fabric` | 26.1.2 / fabric 0.19.2 | 25 | `moderner` (fabric-unobfuscated) | jei 29.16.0.46, fabric-api 0.146.1+26.1.2 |
| `1.21.1-neoforge` | 1.21.1 / neoforge 21.1.215 | 21 | `1.21.1` | jei 19.37.0.362 |
| `1.21.1-fabric` | 1.21.1 / fabric 0.16.14 | 21 | `1.21.1` | jei 19.37.0.362, fabric-api 0.116.1+1.21.1 |
| `mv-1.20.1-forge` | 1.20.1 / forge 1.20.1-47.0.35 | 17 | `multiple-version` | jei 15.48.0.179 |
| `mv-1.20.1-fabric` | 1.20.1 / fabric 0.16.14 (see below) | 17 | `multiple-version` | jei 15.48.0.179, fabric-api (see below) |
| `mv-1.19.2-forge` | 1.19.2 / forge 1.19.2-43.2.3 | 17 | `multiple-version` | jei 11.30.3.1056 |
| `mv-1.19.2-fabric` | 1.19.2 / fabric 0.16.14 (see below) | 17 | `multiple-version` | jei 11.30.3.1056, fabric-api 0.77.0+1.19.2 |
| `mv-1.18.2-forge` | 1.18.2 / forge 1.18.2-40.2.1 | 17 | `multiple-version` | jei 10.2.1.1005 |
| `mv-1.18.2-fabric` | 1.18.2 / fabric 0.14.9 | 17 | `multiple-version` | jei 10.2.1.1005, fabric-api 0.67.0+1.18.2 |
| `mv-1.16.5-forge` | 1.16.5 / forge 1.16.5-36.2.42 | 17 | `multiple-version` | jei 7.8.0.1009 (pinned by file id) |
| `mv-1.16.5-fabric` | 1.16.5 / fabric 0.14.9 | 17 | `multiple-version` | fabric-api 0.42.0+1.16 (smoke) |
| `1.12.2-forge-hei` | 1.12.2 / forge 1.12.2-14.23.5.2864 | 21 | `1.12.0` (srg jar) | had-enough-items 4.34.0 (dev-classpath) |
| `1.12.2-forge-jei-legacy` | 1.12.2 / forge 1.12.2-14.23.5.2864 | 21 | `1.12.0` (srg jar) | jei 4.16.1.301 (dev-classpath) |

Every row also stages the preset `options.txt`. Every row is a **harness row**
today — it declares `uitest` and no `world`, and its selection is in the `uitest`
section above — so the shape is the same everywhere: the level the scenarios are
asserted against is the one that row's harness runner creates, and the client
quits itself when the selection is done. What differs is what each row claims:

| env | runs? | world | why |
| --- | --- | --- | --- |
| `1.21.1-neoforge`, `moderner-1.21.10` ×2 | yes | none — `uitest` | these rows run their own harness build, which stands up and pins its own flat level, and are judged by the harness's own report |
| `1.12.2` ×2 | yes | none — `uitest` | same shape in `uitest/1.12.2-forge`: it creates its own flat creative level, and the rows are judged by its report ("The 1.12.2 rows and the gradle launch") |
| `1.21.1-fabric`, `moderner-26.1.2` ×2, `mv-1.20.1-fabric` | yes | none — `uitest` | the JEI plugin marker needs a loaded level, and the harness's runner creates it (these rows used a quick-play join until they moved; measured 2026-10-14) |
| `mv-1.19.2-forge` | yes | none — `uitest` | the same marker, and quick play offers no join before 1.20 (re-measured 2026-10-14); its harness build creates a level itself (`WorldOpenFlows.createFreshLevel`) |
| `mv-1.20.1-forge` | yes | none — `uitest` | the same marker, reached from its own harness build's level; its checkout's Forge dev run cannot host the mod under test (2026-10-14) |
| `mv-1.19.2-fabric` | yes | none — `uitest` | the same command-line gap, answered the same way: the storage marker arrives inside the harness's own level |
| `mv-1.18.2-forge`, `mv-1.16.5-forge` | yes | none — `uitest` | no level-dependent marker: this line's JECh jar carries no JEI plugin class, so the checks are the startup and failure markers — and the checkout's Forge dev run cannot host the mod either (2026-10-14) |
| `mv-1.16.5-fabric`, `mv-1.18.2-fabric` | yes | none — `uitest` | no level-dependent marker: startup and failure checks (1.16.5 fabric carries no target mod at all) |

No env declares `ready_when` either: every row's harness runner quits the client
when the selection is done (see `uitest`), so no run needs a stopping rule. A
`gradle` row that launches a client nothing makes quit would — `Sound engine
started` is the line the quick-play rows used for it, and the rule is written out
in "When is the game ready?" above.

`mv-1.19.2-forge` was the row with no path to its marker: JEI 11.x's
`registerAdvancedSearch` (the phase that logs JECh's storage marker) runs at
level load, and the only non-interactive way to load a level —
`--quickPlaySingleplayer` — does not exist before 1.20. That was re-measured on
2026-10-14 rather than assumed: with a seed staged and `--world jechworld` on the
command line, the 1.19.2 client reached the title screen and never loaded a
level, because the then-live launcher only passed the quick-play argument to
versions whose
profile carries the `feature:is_quick_play_singleplayer` trait (its
`net.minecraft/1.19.2.json` had no such trait; 1.20.1 does). So the env stays in
the matrix with its checks written down, and `skip` named exactly what was
missing. The row has since left `skip` by the other route: its own harness build
creates the level the marker needs (`WorldOpenFlows.createFreshLevel`, the row's
env comment names it), so no 1.19.2-era command-line world join was needed after
all — and none exists. `mv-1.19.2-fabric` faced the same command-line gap and
never skipped either: before its move it claimed no level-dependent marker, and
its harness build now creates the level the storage marker needs, so both 1.19.2
rows reach that marker the same way (see the `skip` section above).

The version strings above are not invented: they mirror each checkout's
`gradle.properties` (`neo_version`, `forge_version`, `fabric_loader_version`,
`fabric_api_version`, `jei_version`, `jei_unobfuscated_version`, `hei_version`).

The `mv-1.20.1-fabric` row needed three corrections before its dev run would load
its modset at all — two to upstream's properties, one to how the api reaches the
run:

* `1.20.1/gradle.properties` as found said `fabric_api_version=0.76.0+1.19.4`, a
  1.19.4 build. No such file exists for MC 1.20.1 on either CurseForge or
  Modrinth, so the row pins the real 1.20.1 line `0.92.12+1.20.1` instead — the
  newest fabric-api for 1.20.1 — and stays tagged `experimental`.
* the same file's `fabric_loader_version=0.14.11` cannot load that modset: the
  pinned `jei 15.48.0.179` requires `fabricloader >=0.16.3`, so Fabric refuses
  the set with `Incompatible mod set!` before Minecraft starts (measured: the
  first run of this row FAILed with exactly that line and no game session). The
  checkout therefore uses loader `0.16.14`, the version the 1.21.1 fabric row
  already runs.
* that pinned api **cannot be a Gradle mod dependency of the 1.20.1 module at
  all**, which is why nothing holds it as a *mod* dependency: the harness build
  compiles against `0.91.0+1.20.1` and resolves the pinned `0.92.12+1.20.1`
  through a plain configuration into the run's `mods/`. From 0.91.1 on, every
  MC 1.20.1 fabric-api is built by loom
  >= 1.5 and carries `Fabric-Loom-Mixin-Remap-Type` in its manifest; the
  `multiple-version` checkout's architectury-loom 1.1 refuses that key while
  configuring the project (`This version of loom does not support the mixin remap
  type value`). Measured with each token isolated and across 0.91.1 … 0.92.12 —
  all key-bearing, with `0.91.0+1.20.1` the newest such jar loom 1.1 accepts. The
  module therefore holds `modCompileOnly` on `0.91.0+1.20.1` (enough for its two
  `fabric-command-api-v2` imports) and the run gets the real api from `mods/`,
  where Fabric's dev-mode remapper — loom passes `-Dfabric.remapClasspathFile` —
  turns the production jar from intermediary into the run's named namespace,
  exactly as it does for JEI. It has to be >= `0.92.2+1.20.1`: that is the same
  pinned JEI's own floor for `fabric-api`. Verified in the row's log: the mod list
  names `fabric-api 0.92.12+1.20.1` and `fabricloader 0.16.14`.

`mv-1.19.2-fabric` needed the same kind of correction — the pinned JEI is newer
than the versions upstream's properties name — but entirely in the checkout, and
with none of that machinery:

* `1.19.2/gradle.properties` as found said `fabric_api_version=0.68.0+1.19.2` and
  `fabric_loader_version=0.14.11`, while the pinned `jei 11.30.3.1056` requires
  `fabricloader >=0.15.11` and `fabric-api >=0.77.0+1.19.2` — measured: its dev
  run refused the set with `Incompatible mod set!`, naming both floors. The
  checkout now uses loader `0.16.14` (the loader the 1.20.1 and 1.21.1 fabric
  rows run) and api `0.77.0+1.19.2`, which is also the *last* fabric-api build
  for MC 1.19.2. That last fact is what keeps this row cheap: the api that
  satisfies JEI's floor is still readable by the checkout's loom 1.1, so it stays
  a plain `modApi` dependency on the run's classpath (`dev-classpath`), and the
  row's log lists `fabric-api 0.77.0+1.19.2` and `fabricloader 0.16.14`.

Separately, and true of every fabric row rather than of this one: the fabric
variants' `fabric.mod.json` files declare the legacy `"fabric": "*"` dependency
instead of `fabric-api` (only the 26.1.2 variant names `fabric-api`
explicitly), so all fabric rows carry fabric-api, taken from the
`fabric_api_version` their build compiles against.

## Check families

The check sets follow what each version line can actually prove at INFO level.
Which family a version line belongs to is decided by the *built jar*, not by
intuition: a line whose jar contains `me/towdium/jecharacters/JechJeiPlugin.class`
has a JEI plugin and can be asserted on its registration marker.

* **A — JEI plugin present** (moderner ×4, 1.21.1 ×2, mv-1.20.1 ×2, mv-1.19.2 ×2):
  the marker set is
  `log_contains "Registering JEI pinyin search storage"`,
  `log_not_contains "Could not find targets.json. JechTransformer will not be loaded."`,
  `no_crash_reports`, `exit_code_zero`; the fabric rows add
  `log_not_contains "Can't create config file!"` and
  `file_exists config/jecharacters.json`.

  All ten rows carry that set, and all ten load a level when they run: each row's
  own harness build creates the level the marker needs (see `uitest`), including
  the four `mv` rows of this family — `mv-1.19.2-forge`'s marker needs a level no
  command-line join can reach, and the other three reach it from the harness's own
  level the same way (`mv-1.19.2-fabric` was the last to claim the marker, when
  its harness build
  began creating the level). The rows that were measured on a quick-play join
  before that — `1.21.1-fabric`, the two `moderner-26.1.2` rows and
  `mv-1.20.1-fabric` — all reached a level with their marker present a second
  after `joined the game` (re-measured
  2026-10-14). So
  the level-load claim holds for JEI 15 (1.20.1), JEI 19 (1.21.1), JEI 26
  (1.21.10) and JEI 29 (26.1.2): each of them defers `registerAdvancedSearch`
  until a level exists. Every row also adds `uitest_report` to this set:
  each row's own scenarios are the evidence, and the log checks stay as the "the
  mod loaded" half.
* **B — no JEI plugin** (mv-1.16.5 ×2, mv-1.18.2 ×2): the failure markers only —
  `log_not_contains "Could not find targets.json. JechTransformer will not be loaded."`,
  `no_crash_reports`, `exit_code_zero`. These rows have no world: their markers
  are startup markers, and the JECh jars for 1.16.5/1.18.2 contain no
  `JechJeiPlugin.class` at all (`unzip -l <jar> | grep JechJeiPlugin` is empty).

  **`mv-1.16.5-forge` was red, and the redness was the finding — fixed in the
  checkout and re-measured 2026-10-14.** Its built jar refused to load on the
  Forge version its own checkout pinned: `multiple-version/1.16.5/forge/src/
  main/resources/META-INF/mods.toml:18` hardcodes
  `versionRange = "[36.2.41,)"` for `forge`, while
  `multiple-version/1.16.5/gradle.properties` pinned `forge_version=36.2.39`, so
  FML answered `Mod jecharacters requires forge 36.2.41 or above / Currently,
  forge is 36.2.39`, abandoned mod loading and wrote
  `crash-<stamp>-fml.txt`; the client still reached the title screen with a
  broken mod state (`Cowardly refusing to send event … to a broken mod state` in
  `latest.log`, ~1s *before* the ready line) and `no_crash_reports` caught it.
  The mismatch was in the checkout, not in the env, so the checkout was fixed
  rather than the env: `forge_version` is now `36.2.42`, which satisfies the
  jar's own requirement, and the row then ran to `goal-reached` with all four
  checks green — `[PASS] mv-1.16.5-forge in 48.0s`
  (`Data/jech-compat/runs/mv-1.16.5-forge-20261014-145306`). The env's
  `loader.version` moved with it (`1.16.5-36.2.42`); nothing else about the row
  changed at that point. It stages nothing today: both jars ride its own harness
  build (`via: dev-classpath`), because the
  checkout's Forge dev run cannot host them (see "Forge rows and the gradle
  launch"). The first attempt at this verification never ran: the two runs
  recorded on 2026-10-14 08:13/08:16 died in the launcher's `AuthFlow`
  (`令牌刷新失败`),
  which is why this section still described the pre-fix state until the
  2026-10-14 afternoon run above. The rest of this family is green where it runs
  — `mv-1.16.5-fabric` and `mv-1.18.2-fabric` today, `mv-1.16.5-forge` on the
  launcher before it was removed (both forge rows have since left `skip` and run
  their own harness builds, see "Forge rows and the gradle launch");
  `mv-1.16.5-fabric` has no Forge at all, and
  neither 1.18.2 jar declares any dependency block.
* **C — 1.12.2** (both rows):
  `log_contains "Transforming class mezz/jei/ingredients/IngredientFilter for JEI integration."`,
  `log_not_contains "Invalid config syntax:"`,
  `file_exists config/JustEnoughCharacters.cfg`, `no_crash_reports`,
  `exit_code_zero`, and — because both rows are harness rows — `uitest_report`,
  which is what turns the run red when a scenario fails; the legacy-JEI row also
  carries
  `log_contains "Transforming constructor of mezz/jei/suffixtree/GeneralizedSuffixTree"`,
  which the Had Enough Items row must not carry (that ASM patch belongs to the
  legacy-JEI path only), and skips `Registering JEI pinyin search index`.

  Why those two rows do not share that one marker: it is emitted by
  `JechJeiPlugin.registerAdvancedSearch`, i.e. through JEI's *advanced search
  API*, and that API is the Had Enough Items fork's addition — legacy
  `jei_1.12.2-4.16.1.301.jar` exposes 75 classes under `mezz/jei/api/` and
  `IAdvancedSearchRegistry` is not among them (`HadEnoughItems_1.12.2-4.34.0.jar`
  has it, along with `ISearchIndex`, `ISearchIndexFactory`,
  `ISearchIndexBuilder(Factor)`). With legacy JEI the plugin method can never be
  called; JECh integrates there through the classic ASM patch instead
  (`TransformerJei.transformLegacyJei` replaces the `GeneralizedSuffixTree`
  constructor in both `IngredientFilter.<init>` and
  `createPrefixedSearchTree`), which is exactly what the two transformer markers
  assert. Measured 2026-10-14: the legacy row ran to `goal-reached` with both
  transformer markers present and the plugin marker absent; that is the row's
  intended shape, not a JECh regression.

  Both transformer markers quote the **JVM-internal class names, with slashes**.
  That is not a style choice: the runtime logs `ClassNode.name` as ASM holds it
  (`1.12.0`: `Transformer.transform(ClassNode)` logs
  `"Transforming class " + n.name + " for JEI integration."`), while the
  transformer's `accepts(...)` receives the dotted launchwrapper name. Asserting
  the dotted spelling there would be a check that can never pass. Likewise the
  suffix-tree patch is logged as `mezz/jei/suffixtree/GeneralizedSuffixTree` —
  the 1.12.2-era JEI package, not the 1.16+ `mezz/jei/search/suffixtree` one.

  Both rows are harness rows now (revived 2026-10-14) and were `skip`ped for one
  day: their checks above are unchanged from that state — nothing was weakened to
  un-skip them — and what changed is one layer below, the launch. The measurement
  that reopened them, and what the Gradle launch needed for it, are in "The 1.12.2
  rows and the gradle launch" above. The two rows' selections differ on purpose:
  the legacy-JEI row runs `tag:smoke,tag:jei` (including the pinyin scenario,
  whose recorded search it can satisfy), the HEI row `tag:smoke` — HEI answers
  through `IAdvancedSearchRegistry`, not through the legacy suffix-tree
  replacement those scenarios assert on.

## Adding an env

1. Copy the closest existing file, rename it to the new `name`.
2. Read the checkout's `gradle.properties` and copy the loader / api / jei
   versions from there instead of typing a remembered version.
3. Check which family the new line belongs to (`unzip -l <jar> | grep JechJeiPlugin`).
4. Decide what shape the row is, before writing any check: a **harness row**
   (`uitest` with a `project` — a standalone build under `uitest/<row>` that
   creates its own level and quits the client; today's shape for every row), or a
   **`gradle` row** that launches a checkout's own run task, in which case it
   needs `ready_when` and, when a marker needs a loaded level, `world`.
   The shipped-matrix assertions in
   `selftest/test_envs.py` exist to catch a row that picked the wrong one.
5. A harness row gives its `uitest` block a `project` naming that build — without
   one (or without a `gradle` block) the row cannot be executed at all — and
   takes neither `ready_when` nor `world`. A `gradle` row instead needs a
   `ready_when` line from a real run's
   `latest.log` (a first, exploratory `jt run` may end at the watchdog — that log
   is still the evidence to read) and, when
   a marker needs a loaded level, `"world": {"name": "jechworld"}`, picking a line
   the client prints **whether or
   not** the join succeeds (see "When is the game ready?"). `jt run` builds the
   seed itself; `jt seed-world <name>` builds it ahead of time. Its `gradle.args`
   is for a checkout project property that `jt` cannot spell for it (see the
   `gradle` key table); the only one ever needed was `-PdevMod`, for the `1.12.0`
   checkout's own run.
6. If the marker is unreachable for a version-line reason, do not soften the
   check: keep the check as written, add `"skip": "<the missing capability>"`,
   and say so here. That is different from a check whose *premise* is wrong for
   the row — a marker only one loader's API can emit, say (see the legacy-JEI
   row under "Check families"): fix the check, keep what the row does prove, and
   record the evidence here.
7. `jt seed-world <name>` (when step 5 applied), then `jt envs` (does it load?),
   then `jt resolve <name>` (does the description pin to exactly one file?),
   then `jt run <name>`.
