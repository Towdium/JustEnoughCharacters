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
    { "type": "no_crash_reports", "name": "no-crash-reports" },
    { "type": "exit_code_zero", "name": "exit-code" }
  ],
  "ready_when": { "value": "Sound engine started" },
  "world": { "name": "jechworld" },
  "timeout_sec": 600,
  "jvm": { "max_mem_mb": 3072, "min_mem_mb": 1024, "window": [1280, 720] }
}
```

### Top level

| key | required | meaning |
| --- | --- | --- |
| `name` | yes | env id, `[A-Za-z0-9._-]+`; also the run-id prefix and the Prism instance id |
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
| `gradle` | no | `{task, project_dir, daemon_java}` — run the checkout's Gradle run task instead of launching a Prism instance, see below |
| `uitest` | no | `{selection}` — run the checkout's **in-client UI test harness** with that scenario selection, and judge the run by the harness's own report, see below |
| `timeout_sec` | no | watchdog budget, default 600; the whole run is killed and the run fails with exit code 3 |
| `jvm` | no | `{max_mem_mb, min_mem_mb, args, window}`; defaults 3072 / 1024 / `[]` / `[1280, 720]` |

### `gradle` — launching through the checkout instead of Prism

An env with a `gradle` block is executed by running a Gradle task in the checkout
under test (`./gradlew <task> --no-daemon -PjechRunDir=<game dir>`, plus
`-PjechQuickPlayWorld=<world>` when the env declares a world) instead of by
launching a Prism instance. Everything else — resolution, the world seed, the
pack, isolation, the watchdog, `ready_when`, the checks — is unchanged, and the
report records which backend ran (`runBackend`, and a `gradle` object with the
task and checkout). See [README.md](../README.md), "The `gradle` backend",
for the full contract between the harness and a checkout.

Ten rows use it today: six in the modern checkouts, four in the older line.

* `1.21.1-{neoforge,fabric}` and `moderner-{1.21.10,26.1.2}-{neoforge,fabric}`
  (the four moderner rows since 2026-10-14, running `:neoforge:runClient`,
  `:fabric-legacy:runClient`, `:neoforge-unobfuscated:runClient` and
  `:fabric-unobfuscated:runClient` in the `moderner` checkout). Their
  `daemon_java` is `21` for the 1.21.1 pair (Gradle 8.13) and `null` for the
  moderner rows (Gradle 9.5 runs on the machine's default JDK). One of these six
  does not run `runClient`: `1.21.1-neoforge` runs `:neoforge:runClientTests`,
  the checkout's UI test harness run (see `uitest` below).
* the four `multiple-version` fabric rows, migrated 2026-10-14:
  `mv-1.16.5-fabric`, `mv-1.18.2-fabric`, `mv-1.19.2-fabric` and
  `mv-1.20.1-fabric`, running `:<mc>:fabric:runClient` in that checkout. Their
  `daemon_java` is `17`: the wrapper is Gradle 8.1.1, which cannot run on the
  machine's default JDK. The four modules already carried the harness wiring
  (`-PjechRunDir` / `-PjechQuickPlayWorld` into loom's default client run); what
  the migration had to close lives in the checkout, and is written into the
  module's own build files rather than into an env:

  * **PinIn on the dev runtime classpath.** Every impl module takes
    `:common-main` with `compileOnly`, so that module's `api PinIn` reaches the
    compile classpath and the shadow configurations but never `runClient`, which
    died in `JechConfig.<clinit>` with
    `NoClassDefFoundError: me/towdium/pinin/Keyboard` on 1.18.2 and 1.19.2. The
    root `build.gradle` now adds `runtimeOnly PinIn` to every impl module (both
    loaders, `transitive = false`); `shadowJar` reads only `shadowCommon`, so no
    shipped jar changes.
  * **`mv-1.16.5-fabric`'s loader libraries.** Loom injects a
    fabric-loader-0.10.5-era set for MC 1.16.5 while `modImplementation
    fabric-loader` puts 0.14.9 on the run classpath, so
    `1.16.5/fabric/build.gradle` also declares `access-widener` 2.1.0 and
    `sponge-mixin` 0.11.4+mixin.0.8.5 as `runtimeOnly`. The lines carry the
    measurement (`KnotClient` died with `NoClassDefFoundError` on
    `net.fabricmc.accesswidener.AccessWidener` before any window).
  * **`mv-1.20.1-fabric`'s fabric-api is `game-dir`, not `dev-classpath`.** Its
    pinned api cannot be a Gradle mod dependency of that module at all — see the
    row's note below.

  The four rows' `via` split (JECh `dev-classpath` in all four, JEI `game-dir` in
  the three that carry it, fabric-api `dev-classpath` except in `mv-1.20.1-fabric`)
  is recorded in `selftest/test_envs.py`.

| key | required | meaning |
| --- | --- | --- |
| `task` | yes | the Gradle task, spelled from the build root (`:neoforge:runClient`) |
| `project_dir` | no | the checkout to run in, relative to `$JT_JECH_ROOT`; defaults to the env's `jech.project`, so the task always runs where the artifact under test was built |
| `daemon_java` | no | the major version of the JDK that runs **Gradle** (its `JAVA_HOME`), e.g. `21` for a Gradle 8.13 wrapper on a Java 25 machine; absent/`null` keeps the ambient `JAVA_HOME`. This is not the client's JDK: that comes from the checkout's own toolchain |

`env.jech` and each `mods[]` entry take one more optional key:

| key | values | meaning |
| --- | --- | --- |
| `via` | `game-dir` (default) / `dev-classpath` | where the artifact's jar goes. `game-dir` copies it into the game directory's `mods/` — what a production instance needs. `dev-classpath` stages nothing: the checkout's own run provides it (the module's sources registered as the mod, or a declared `localRuntime` / `modImplementation` dependency). It is still resolved, pinned and reported; staging it as well would load the same mod id twice |

A `via` that contradicts what the checkout actually provides is not detectable
from the env file — the ten gradle rows are the written record of the choice (see
the checks in `selftest/test_envs.py`), and a wrong one shows up as a duplicate-mod
error in the run's log rather than as a silent pass. All four `moderner` rows are
`dev-classpath` for every artifact, including JECh: each of that line's modules
already declares its own runtime dependencies for JEI (both NeoForge modules,
`localRuntime` at exactly the pinned version; `fabric-legacy`, `modRuntimeOnly`;
`fabric-unobfuscated`, `runtimeOnly`) and for fabric-api, and the module under
test *is* the run's own source set. The four `multiple-version` fabric rows take
JECh `dev-classpath` for the same reason, stage JEI (the version-level build
files hold it `compileOnly`), and split on fabric-api: `dev-classpath` in the
three modules that declare it `modApi`, `game-dir` in `mv-1.20.1-fabric`, whose
pinned api cannot be a Gradle mod dependency at all (see that row's note).

`loader.type` is one of `forge`, `neoforge`, `fabric`, `quilt`.

`loader.version` is the **mrpack dependency string** (`lib/pack.py` writes it into
`modrinth.index.json` verbatim):

| loader | the value | example |
| --- | --- | --- |
| `neoforge` | the bare NeoForge version | `21.1.215`, `21.10.64`, `26.1.2.59-beta` |
| `forge` | `<mc>-<forge version>` | `1.20.1-47.0.35`, `1.12.2-14.23.5.2864` |
| `fabric` | the **fabric-loader** version (not the fabric-api version) | `0.16.14`, `0.19.2` |
| `quilt` | the quilt-loader version | |

### Forge rows and the gradle backend

The four `multiple-version` forge rows are the only rows *inside* a migrated
version line that stayed on Prism — the fabric half of the same checkout runs on
the gradle backend. That split is measured, not assumed: on 2026-10-14 the four
modules were wired the same way the fabric ones were (property guards in the
client run, `targets.json` through `processResources`, JEI as a dev-only mod
dependency, `daemon_java: 17`) and each module was then run bare, with no harness
property, to separate "the checkout cannot be dev-run" from "the harness wires it
wrongly". Two independent blockers came out, and neither is in the harness:

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
  for 1.18.2 with a different module hash. The rows therefore stay on Prism,
  where they are green.

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
  `transformProductionForge`/`remapJar` and is what the Prism row stages, and the
  row is green (measured 2026-10-14). What is broken is only a development run.

Both blockers belong to the checkout, so the probe's module edits were reverted
(the checkout is as upstream has it, plus the `forge_version` bump) and the rows
run on Prism. The recipe is recorded here so a future pass can re-apply it in one
step once the split package is dealt with:

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

### The 1.12.2 rows and the gradle backend

Both `1.12.2-*` rows are **declared `skip`** (2026-10-14): on this machine a
1.12.2 client cannot be dev-run at all, and the instance backend that used to
carry them is retired. Measured end to end with the checkout's own task and no
harness property in play (`Data/jech-compat/112-gradle-20261014/`, `1.12.0`
checkout, Gradle 9.3.1, client toolchain 8):

* **The bare run does not even resolve.** `./gradlew runClient` in `1.12.0` dies
  in `:runtimeClasspath`: Forge's maven mirror serves
  `ca.weblite:java-objc-bridge:1.0.0`'s POM and its main jar but **404s the
  `natives-osx` classifier jar** the 1.12.2 `classes-macos` variant selects
  (`maven.minecraftforge.net/.../java-objc-bridge-1.0.0-natives-osx.jar` → 404,
  while the same file on `libraries.minecraft.net` → 200, 5629 bytes). Gradle
  pins a module to the first repository that answers with metadata, so the mirror
  that has the POM wins and the one that has the jar is never asked. That is
  repository rot, not a checkout defect, and one declaration works around it —
  see the recipe below. With it added, the task gets past resolution and launches
  the client.
* **Then the client cannot load a window.** The launcher
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
  client's `nativesDirectory` at them.
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
  `lwjgl-2.9.4`). No env file can add that: it is a launcher-side natives
  substitution, i.e. exactly the capability a Prism-free future drops.
* **Names are not the blocker here, though — that expectation is settled the
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
  direction. **Untested, however:** the client never reached mod loading —
  `Searching <game dir>/mods for mods` is the last FML line before the native
  crash — so "JEI 4.16.1 loads in a dev run" is an expectation, not a
  measurement.
* **What already works, so a future pass does not redo it.** The checkout's
  coremod *is* discovered in a dev run (`Found and added coremod:
  me.towdium.jecharacters.core.JechCore`, and
  `Found a command line coremod : me.towdium.jecharacters.core.JechCore` — the
  launcher turns the project's `META-INF/MANIFEST.MF` into
  `-Dfml.coreMods.load`), `<game dir>/mods` is scanned, and JECh's coremod ran
  far enough to write `<game dir>/config/JustEnoughCharacters.cfg`
  (`JechCore.injectData` → `JechConfig.init`), which is the row's
  `config-generated` check. The untested half is the mod that has to load after
  Minecraft bootstraps.
* **The recipe, for whenever someone gives the backend natives.** All of it is
  outside the env files:
  1. give the module a repository that still serves the macOS native classifier,
     declared **first** and group-restricted so the module resolves where the POM
     *and* the jar both exist:

     ```groovy
     repositories {
         maven { url = 'https://libraries.minecraft.net/'; content { includeGroup 'ca.weblite' } }
         minecraft.mavenizer(it)   // then fg.forgeMaven, fg.minecraftLibsMaven, … as upstream
     }
     ```
  2. make the client able to load an arm64 window: either substitute an arm64
     LWJGL 2 natives set into the run the way the launcher's meta does (the same
     `natives-osx-arm64` jar, plus a `java.library.path` that wins over the
     slime-launcher's natives directory — the LWJGL version on the run classpath
     is part of that substitution), or run the client on an x86_64 JDK 8 under
     Rosetta (none is installed, and an env has no field for the client's JDK by
     design);
  3. only then re-probe whether the two rows' markers are reachable in a dev run.
     If they are, wire `runClient` the way the property-guarded recipe above does
     (`jechRunDir`; a 1.12.2 row is menu-level, since there is no quick-play
     before 1.20).

Until then both rows carry `skip` with the measured reason, their checks stay
written down (nothing was weakened to make them go away), and the `1.12.0`
checkout is left as upstream has it — the probe's repository declaration was
reverted. The probe and control logs are
`Data/jech-compat/112-gradle-20261014/` (`01-control-nomods.log`,
`02-control-nomods-info.log`, `03-probe-jei-legacy.log`).

### `uitest` — running the checkout's in-client UI test harness

```json
"uitest": { "selection": "tag:smoke,tag:jei" }
```

An env with a `uitest` block runs the checkout's own **in-client UI test
harness**: a dev-only mod that drives the real client through scripted scenarios
(keyboard and chat input, JEI's own search bar) and writes its own verdict. The
selection is handed to Gradle as `-PjechTest=<selection>`, the only gate on that
wiring, so the env's `gradle.task` must be the harness's own run task
(`:neoforge:runClientTests` in the 1.21.1 checkout), which honours `-PjechRunDir`
exactly as `runClient` does.

What changes for such a row:

* **The client ends itself.** The harness quits the game once the selection is
  done, so the row declares **no `ready_when`**: a stopping rule would end the
  session mid-scenario and leave the run without a report at all. This is the one
  exception to "every executed env declares a stopping rule", and
  `selftest/test_envs.py` checks both halves of it.
* **The level is the harness's own.** The harness runner creates and pins its own
  flat level and deletes the stale ones it owns, so the row declares **no
  `world`** either: a quick-play join would race the level the scenarios are
  asserted against. (The harness run ignores `-PjechQuickPlayWorld` for the same
  reason — see the checkout's `gradle/jech-uitest.gradle`.)
* **The evidence is the report.** The harness writes `report.json`,
  `report.txt`, `junit.xml` and `screenshots/` into `<game dir>/uitest` before it
  quits, and the run is judged by that report through the `uitest_report` check.
  The log checks stay as well: they say the mod loaded, the report says the
  scenarios ran.
* **A failing scenario is red twice over.** The harness exits non-zero when a
  scenario failed, which fails the Gradle build through the checkout's own
  `verifyJechUiTest`, and `uitest_report` fails on the report. The captures to
  look at are under `<game dir>/uitest/screenshots/<scenario>/`.

| key | required | meaning |
| --- | --- | --- |
| `selection` | yes | the harness's scenario selection, passed to Gradle verbatim: `all`, a scenario name, `a,b` (terms are OR-ed), `tag:<tag>`, `group:<group>`, `mod:<mod>`, `regex:<pattern>` — the harness's own `ScenarioSelection` grammar. **Every term carries its own prefix**, because a bare term is a *name* match: `tag:smoke,tag:jei`, never `tag:smoke,jei` (which would silently run the smoke scenarios alone) |

The selection names the scenarios the row was measured with instead of saying
`all`, for the same reason a row's `world` is written down: a row asserts what it
was measured with, and a scenario added later cannot silently change that.
`1.21.1-neoforge` is the only row that runs the harness today, with
`tag:smoke,tag:jei` (four scenarios: `smoke` and `json-smoke` by tag, and the two
JEI ones by tag). The scenarios themselves — and the step vocabulary a scenario
is written in — are documented in the checkout, next to the scenarios
(`neoforge/src/uitest/resources/uitest/scenarios/README.md`).

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
| `file_exists` | ✔ relative path(s) | every path exists **under the instance's `minecraft/` directory** (e.g. `config/jecharacters.json`) |
| `no_crash_reports` | — | no new file appeared in `minecraft/crash-reports/` during the run |
| `exit_code_zero` | — | the game ended healthy: the runner deliberately stopped it once it was ready, or the game itself exited 0 (SKIP when Prism handed off to an already-running instance *and* nothing ended it deliberately) |
| `uitest_report` | optional relative path | the in-client UI test harness reported `PASS` in `uitest/report.json` (or the named path) with no scenario failed or errored. The report is written by the harness itself before it quits the client, so a missing, unreadable or unknown-schema report **FAILs**: "the scenarios ran" is what the check is here to establish, and an unevaluated verdict is not a pass. See `uitest` above |

Three rules keep the checks honest:

* A check whose precondition is missing **SKIPs** — it is never rendered as a
  pass. `log_*` checks SKIP when no new game session log appeared; `file_exists`
  SKIPs when no instance directory was located. The runner always adds three
  `internal:` checks (`session-started`, `watchdog`, `game-exit`) that FAIL in
  exactly those cases, so a run with missing evidence fails on a named check.
  `uitest_report` is the deliberate exception to the SKIP half: it only SKIPs when
  no instance directory was located at all, and otherwise FAILs on a report it
  cannot read, because a missing report *is* the failure it names.
* Only **INFO-level** markers are assertable. A production instance logs at INFO
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
  own flat level (`1.21.1-neoforge` does, see `uitest` above).

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

Three rows carry `skip` today: `mv-1.19.2-forge`, below, and the two `1.12.2-*`
rows, whose reason is a launch-layer one (the checkout's Gradle dev run cannot
boot a 1.12.2 client on this arm64 machine, and the instance backend that
substituted arm64 natives for it is retired — "The 1.12.2 rows and the gradle
backend"). The `mv-1.19.2-forge` skip was re-measured on
2026-10-14 rather than inherited: `mv-1.19.2-forge` was run with a real seed
staged (`saves/jechworld`, 14 files) and launched as
`prismlauncher --launch … --world jechworld`. The client reached
`Sound engine started` and sat at the title screen for three minutes — no
`Loading level`, no `Preparing spawn area`, no `joined the game` anywhere in
`latest.log` — so no level was ever loaded and the marker could not appear.
That is Prism behaving as designed, not a harness failure: Prism only appends
the quick-play game argument when the version profile carries the
`feature:is_quick_play_singleplayer` trait, and Prism's own metadata has that
trait from 1.20 on (`net.minecraft/1.19.2.json` `+traits`:
`["XR:Initial","FirstThreadOnMacOS"]`; `1.20.1`/`1.21.1` add both quick-play
traits), while a world target has no fallback argument (the server case falls
back to `--server`/`--port`; a world does not). The env was restored to `skip`
with its checks intact after the probe.

Its fabric sibling sits in the same position — MC 1.19.2 offers no
non-interactive way to load a level on either backend — but it does not skip,
because on the gradle backend it does not have to claim a level at all.
`mv-1.19.2-fabric` is a **menu-level smoke row**: it declares no `world` and no
storage-marker check, and asserts what a client that stops at the title screen
can show (the transformer loaded, the config was written, no crash report). Its
env comment carries the same reasoning, and both halves are asserted in
`selftest/test_envs.py` and `selftest/test_worlds.py`.

## Launching into a world (`world`)

```json
"world": { "name": "jechworld" }
```

An env with `world` is launched with Prism's own *join world on launch* option —
`prismlauncher -d <root> --launch <instance> --world <name>` — and the seed for
that world is staged as `saves/<name>/` inside the instance on every run.
`--world` is a **first-class Prism option**, not a game-argument passthrough:
Prism knows which game argument each Minecraft version wants
(`--quickPlaySingleplayer` for 1.20+) and builds it itself. The two paths that
were tried and rejected are recorded here so nobody re-treads them:

* `prismlauncher --launch <id> -- --quickPlaySingleplayer <world>`: Qt parses
  everything after `--` as a **positional url**, so the trailing arguments become
  *resource imports* to the launcher, not game arguments. Measurement: with
  `-- --quickPlaySingleplayer jechworld` the launcher opens and stays open, no
  world is joined.
* A per-instance `.minecraft/patches/*.json` version patch appending to
  `arguments.game`: workable in principle, but it only ever duplicates what
  Prism already does, and a patch file has to be kept in sync per loader. Not
  used.

Which rows declare it, as measured on 2026-10-14: `1.21.1-fabric`, the four
`moderner` rows and the two `mv-1.20.1` rows — all seven run to
`stopReason: goal-reached` on a real join (`vfyjxf_ joined the game`, then
JECh's `Registering JEI pinyin search storage` a second later).
`1.21.1-neoforge` left the list when it moved to the in-client harness
(2026-10-14): the level it is asserted against is the one the harness's own
runner creates, so the row declares no `world` (see `uitest` above) — the marker
still arrives, because the level does. The `mv-1.16.5` and `mv-1.18.2` rows
deliberately keep no world: their markers are emitted at startup (and the
1.16.5/1.18.2 jars carry no JEI plugin at all), so a world would only add a level
load to what they assert. `mv-1.19.2-forge` cannot join one — see the `skip`
section above — while `mv-1.19.2-fabric` is the same MC version but a gradle row
that claims no marker, so it is a menu-level row by design: no world, no marker.

A `gradle` env carries the same world a different way: the harness hands it over
as `-PjechQuickPlayWorld=<name>` and the checkout's own run configuration appends
`--quickPlaySingleplayer <name>` to the client's arguments — no Prism option is
involved, and the game directory is the run's own `runs/<id>/game`. The six
gradle rows that declare a world (`1.21.1-fabric`, `moderner` ×4 and
`mv-1.20.1-fabric`) join that
way; the seed is staged into `saves/<name>/` there exactly as it is for a Prism
instance. `1.21.1-neoforge`'s `runClientTests` run deliberately does not honour
the property: the harness's runner does its own level management.

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
and re-stages it into the instance on every run (a world a previous run played
in is replaced, never merged).

## Launching without a world

An env without `world` stops at the title screen. The preset `options.txt`
(below) is still staged, so the client reaches the menu; that is the right shape
for envs whose markers are emitted during startup (the 1.12.2 rows, the 1.16.5 /
1.18.2 rows).

## The preset `options.txt`

A freshly materialized instance has no `options.txt`, and a client with no
`options.txt` shows the **"Welcome to Minecraft!" accessibility onboarding
screen before the title screen** — a modal page with a Continue button that
nothing in a headless run can press, so the run never reaches the menu. `jt`
therefore renders a minimal preset into the pack (`overrides/options.txt`, see
`lib/options.py`) and stages it into the instance on every run:

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
* `lang:zh_cn` starts the client in Simplified Chinese. That is a fixture, not a
  taste in speech: JECh is a *pinyin* search mod, a pinyin query only has
  anything to search once the items carry Chinese names (`block.minecraft.stone`
  is `石头`, which `shitou` reaches; in `en_us` it is `Stone`, which no pinyin
  can), and the in-client harness asserts exactly that. A version that spells the
  language code differently (`1.12.2` uses `zh_CN`) falls back to `en_us` with a
  warning instead of failing to load.

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
nothing was lost: a fresh instance has no override, so the binding the harness
scenario presses is JEI's own registered default (`key.keyboard.f` + `CONTROL`).
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

Verified on a freshly materialized 1.21.1 instance: with the preset the client
lands on the title screen, without it the onboarding screen comes first.

## When is the game ready? (`ready_when`)

A production instance has no in-game runner: nothing in the game makes it quit,
so without a stopping rule a `jt run` can only end by the game being closed by
hand or by the watchdog (exit code 3). `ready_when` gives the run a stopping
rule.

```json
"ready_when": { "value": "Sound engine started" }
```

* **The rule.** The run is *ready* once the `ready_when` line is in
  `latest.log` **and** every `log_contains` value of the env is in it too. At
  that moment the runner ends the instance's process tree itself and judges the
  run; the report records `stopReason: "goal-reached"` and `readyReached: true`.
* **What the report tells you when it did not hold.** `readyReached` is the whole
  rule, so a `false` on its own does not say which half was missing. The report
  prints both halves separately: `readyLineSeen` (was the ready line in the log?)
  and `missingMarkers` (which `log_contains` values never arrived). On the
  1.21.1 rows the ready line lands early and the JEI marker only at world load,
  so the honest reading of a pre-`world` failure there is
  `readyLineSeen: true, missingMarkers: ['Registering JEI pinyin search storage']`
  — the marker was the missing half, not the ready line.
* **Choosing the line.** Pick a stable INFO line vanilla prints exactly once
  when the client has finished coming up (for 1.12.2 and 1.21.1 alike, the
  classic `Sound engine started` / `Setting user:` family is a good start);
  verify it against a real `latest.log` before trusting it. Order matters: since
  every `log_contains` marker must already be there, either pick a ready line
  that comes *after* your markers, or accept that the run waits for the markers.

  For an env with `world`, keep a line that the client prints **whether or not**
  the world joins (both 1.21.1 rows use `Sound engine started`, which lands ~4s
  before the join). A world-only line such as
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
* **A launcher that exits at once.** Prism's CLI hands a launch off to a Prism
  process that already owns the data root (a local socket), and then exits 0 in
  under a second with the game starting milliseconds later. The runner does not
  read that as "no session": it keeps watching for `HANDOFF_GRACE_SEC` (120s)
  for a session or a game process, and records the situation in the notes,
  naming the other launcher's pid when there is one. A genuinely failed launch
  still ends as a missing session after that grace.
* **Omitting it.** Without `ready_when` the run behaves as before: it waits for
  the game to exit on its own or for the watchdog. No shipped env omits it: a
  modded client that reaches the title screen never exits on its own, so an env
  without a stopping rule can only end in a watchdog kill (exit 3) — i.e. it is
  a broken env, not a lenient one. Every other env file in `envs/` declares
  `Sound engine started`, which the client prints in 1.12.2 through 26.1.2 alike
  (verified against each client jar), and which lands *before* the world join so
  a failed join still becomes a clean `ready-timeout`.
* **The one row that omits it, and must.** An env with a `uitest` block runs the
  checkout's in-client harness, whose own runner quits the client when the
  selection is done. A stopping rule there would end the session mid-scenario and
  leave the run with no report — so such a row declares no `ready_when`, and the
  absence is the correct shape rather than a broken env (`1.21.1-neoforge`; see
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
| `mv-1.20.1-fabric` | 1.20.1 / fabric 0.16.14 (see below) | 17 | `multiple-version` (gradle) | jei 15.48.0.179, fabric-api (see below) |
| `mv-1.19.2-forge` | 1.19.2 / forge 1.19.2-43.2.3 | 17 | `multiple-version` | jei 11.30.3.1056 — **skipped**, see below |
| `mv-1.19.2-fabric` | 1.19.2 / fabric 0.16.14 (see below) | 17 | `multiple-version` (gradle) | jei 11.30.3.1056, fabric-api 0.77.0+1.19.2 |
| `mv-1.18.2-forge` | 1.18.2 / forge 1.18.2-40.2.1 | 17 | `multiple-version` | jei 10.2.1.1005 |
| `mv-1.18.2-fabric` | 1.18.2 / fabric 0.14.9 | 17 | `multiple-version` (gradle) | jei 10.2.1.1005, fabric-api 0.67.0+1.18.2 |
| `mv-1.16.5-forge` | 1.16.5 / forge 1.16.5-36.2.42 | 8 | `multiple-version` | jei 7.8.0.1009 (pinned by file id) |
| `mv-1.16.5-fabric` | 1.16.5 / fabric 0.14.9 | 8 | `multiple-version` (gradle) | fabric-api 0.42.0+1.16 (smoke) |
| `1.12.2-forge-hei` | 1.12.2 / forge 1.12.2-14.23.5.2864 | 8 | `1.12.0` (srg jar) | had-enough-items 4.34.0 — **skipped**, see below |
| `1.12.2-forge-jei-legacy` | 1.12.2 / forge 1.12.2-14.23.5.2864 | 8 | `1.12.0` (srg jar) | jei 4.16.1.301 — **skipped**, see below |

Every row also stages the preset `options.txt`. The seven envs that are
*launched into a world*, the row that loads its own, the row that claims no level
at all, and the ones that are *skipped*, are called out below and in the table
above:

| env | runs? | world | why |
| --- | --- | --- | --- |
| `1.21.1-neoforge` | yes | none — `uitest` | the row runs the checkout's in-client harness, which stands up and pins its own flat level, and is judged by the harness's own report |
| `1.21.1-fabric` | yes | `world: {name: jechworld}` | the JEI plugin marker needs a loaded level |
| `moderner` ×4, `mv-1.20.1` ×2 | yes | `world: {name: jechworld}` | same marker, same level-load phase (measured 2026-10-14) |
| `mv-1.19.2-forge` | **no** — `skip` | none | same marker, but quick play needs 1.20+ (re-measured 2026-10-14) |
| `mv-1.19.2-fabric` | yes | none | the menu-level smoke row: no marker to reach, so no level (gradle backend) |
| `mv-1.16.5` ×2, `mv-1.18.2` ×2 | yes | none | markers are emitted during startup |
| `1.12.2` ×2 | **no** — `skip` | none | markers are emitted during startup, but no launch path boots a 1.12.2 client on this machine (2026-10-14) |

Every env but `1.21.1-neoforge` also declares
`ready_when: Sound engine started`; without it a client that reaches the title
screen runs until the watchdog (see "Omitting it"). The harness row omits it on
purpose: its own runner quits the client, so a stopping rule would end the run
mid-scenario (see `uitest`).

`mv-1.19.2-forge` is the row with no path to its marker: JEI 11.x's
`registerAdvancedSearch` (the phase that logs JECh's storage marker) runs at
level load, and the only non-interactive way to load a level —
`--quickPlaySingleplayer` — does not exist before 1.20. That was re-measured on
2026-10-14 rather than assumed: with a seed staged and `--world jechworld` on the
command line, the 1.19.2 client reached the title screen and never loaded a
level, because Prism only passes the quick-play argument to versions whose
profile carries the `feature:is_quick_play_singleplayer` trait (Prism's
`net.minecraft/1.19.2.json` has no such trait; 1.20.1 does). So the env stays in
the matrix with its checks written down, and `skip` names exactly what is
missing. Removing the `skip` is a follow-up that needs one of: a 1.19.2-era way
to join a world from the command line, or an in-game runner (a test mod that
loads a world and quits). Neither is in place; a weaker assertion is not an
option. `mv-1.19.2-fabric` faces the same missing capability but is not in this
position: as a gradle row it claims no marker, so there is nothing it can fail to
reach and nothing to skip (see the `skip` section above).

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
  all**, which is why the row stages it (`via: game-dir`) instead of declaring it
  `dev-classpath`. From 0.91.1 on, every MC 1.20.1 fabric-api is built by loom
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
  pinned JEI's own floor for `fabric-api`. Verified in the row's log:
  `Loading 61 mods` lists `fabric-api 0.92.12+1.20.1` and `fabricloader 0.16.14`.

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

  Nine of the ten rows carry that set. The exception is `mv-1.19.2-fabric`, the
  menu-level gradle row: its jar is in this family (it has
  `JechJeiPlugin.class`) but the row claims nothing of the storage marker, so it
  keeps the two config checks and the failure/exit checks only (see the `skip`
  section above).

  Those nine rows load a level when they run, except `mv-1.19.2-forge`, which
  carries `skip` (no quick play before 1.20; re-measured 2026-10-14). Seven
  declare `world` — the quick-play rows, `mv-1.20.1-fabric` among them on the
  gradle backend; `1.21.1-neoforge` gets its level from the in-client harness
  instead (see `uitest`). All seven were measured against a real run —
  `1.21.1` ×2, `moderner` ×4, `mv-1.20.1` ×2 (the mv-1.20.1 pair on 2026-10-14,
  and its fabric half again in the gradle-backend migration) all reach a level
  with their marker present a second after `joined the game`. So
  the level-load claim holds for JEI 15 (1.20.1), JEI 19 (1.21.1), JEI 26
  (1.21.10) and JEI 29 (26.1.2): each of them defers `registerAdvancedSearch`
  until a level exists. `1.21.1-neoforge` adds `uitest_report` to this set: the
  row's own scenarios are the evidence, and the log checks stay as the "the mod
  loaded" half.
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
  changed, and it still stages the **production** jar into `mods/`, because the
  checkout's Forge dev run cannot host that jar (see "Forge rows and the gradle
  backend"). The first attempt at this verification never ran: the two runs
  recorded on 2026-10-14 08:13/08:16 died in Prism's `AuthFlow` (`令牌刷新失败`),
  which is why this section still described the pre-fix state until the
  2026-10-14 afternoon run above. The other three rows of this family are green;
  `mv-1.16.5-fabric` has no Forge at all, and neither 1.18.2 jar declares any
  dependency block.
* **C — 1.12.2** (both rows):
  `log_contains "Transforming class mezz/jei/ingredients/IngredientFilter for JEI integration."`,
  `log_not_contains "Invalid config syntax:"`,
  `file_exists config/JustEnoughCharacters.cfg`, `no_crash_reports`,
  `exit_code_zero`; the legacy-JEI row also carries
  `log_contains "Transforming constructor of mezz/jei/suffixtree/GeneralizedSuffixTree"`,
  which the Had Enough Items row must not carry (that ASM patch belongs to the
  legacy-JEI path only), and **skips** `Registering JEI pinyin search index`.

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

  Both rows are declared `skip` (2026-10-14). The checks above stay as the
  written record of what each row verifies; what stops them is one layer below,
  the launch: no path on this machine boots a 1.12.2 client any more. The
  measurement, and what a future pass would have to give the Gradle backend, are
  in "The 1.12.2 rows and the gradle backend" above.

## Adding an env

1. Copy the closest existing file, rename it to the new `name`.
2. Read the checkout's `gradle.properties` and copy the loader / api / jei
   versions from there instead of typing a remembered version.
3. Check which family the new line belongs to (`unzip -l <jar> | grep JechJeiPlugin`).
4. Decide what shape the row is, before writing any check: a **Prism row** that
   stops at the menu, a **quick-play row** that is launched into a world, or a
   **harness row** that runs the checkout's own in-client scenarios (`uitest`).
   The three shapes differ in exactly which of `ready_when` / `world` / `uitest`
   the row carries, and the shipped-matrix assertions in
   `selftest/test_envs.py` exist to catch a row that picked the wrong one.
5. For a Prism or quick-play row, give it a `ready_when` line from a real run's
   `latest.log` (a first, exploratory `jt run` may end at the watchdog — that log
   is still the evidence to read), and add `"world": {"name": "jechworld"}` when
   a marker needs a loaded level, picking a line the client prints **whether or
   not** the join succeeds (see "When is the game ready?"). `jt run` builds the
   seed itself; `jt seed-world <name>` builds it ahead of time. A harness row
   takes neither: it declares `uitest` and nothing about stopping.
6. If the marker is unreachable for a version-line reason, do not soften the
   check: keep the check as written, add `"skip": "<the missing capability>"`,
   and say so here. That is different from a check whose *premise* is wrong for
   the row — a marker only one loader's API can emit, say (see the legacy-JEI
   row under "Check families"): fix the check, keep what the row does prove, and
   record the evidence here.
7. `jt seed-world <name>` (when step 5 applied), then `jt envs` (does it load?),
   then `jt resolve <name>` (does the description pin to exactly one file?),
   then `jt run <name>`.
