package me.towdium.jecharacters.uitest.engine;

import net.minecraft.client.Minecraft;
import net.minecraft.util.datafix.codec.DatapackCodec;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Dimension;
import net.minecraft.world.DimensionType;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.DimensionSettings;
import net.minecraft.world.gen.FlatChunkGenerator;
import net.minecraft.world.gen.FlatGenerationSettings;
import net.minecraft.world.gen.settings.DimensionGeneratorSettings;
import net.minecraft.world.storage.SaveFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Stream;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Creates the throwaway world a run uses, and sweeps the ones earlier runs left behind.
 *
 * <p>The world is a fixed-seed superflat level in creative with cheats on, so two runs share a
 * backdrop and a scenario can place blocks without any setup of its own. Sweeping stale worlds is
 * not housekeeping: two runs of the same run id would otherwise collide on the save directory, and
 * a leftover locked world is the single biggest source of flakiness in this kind of harness.
 *
 * <p><b>Port note (1.21.1 → 1.16.5).</b> The 1.21.1 copy creates a level through
 * {@code Minecraft.createWorldOpenFlows()} and reopens one through {@code openWorld(name,
 * onFailure)}; neither exists in 1.16.5 — the whole single-player flow lives on {@link Minecraft}
 * itself here. Creation is {@code Minecraft.createLevel(String, WorldSettings,
 * DynamicRegistries.Impl, DimensionGeneratorSettings)} and rejoin is
 * {@code Minecraft.loadLevel(String)}, the call the world list's own
 * {@code WorldSelectionList.WorldListEntry.joinWorld()} tail makes. The names are the MCP ones
 * this line is mapped to (see build.gradle): {@code LevelSettings} is {@link WorldSettings},
 * {@code WorldGenSettings} is {@link DimensionGeneratorSettings}, {@code RegistryAccess.builtin()}
 * is {@code DynamicRegistries.builtin()}, {@code LevelStem} is {@link Dimension} and
 * {@code FlatLevelSource}/{@code FlatLevelGeneratorSettings} are
 * {@link FlatChunkGenerator}/{@link FlatGenerationSettings}.
 */
public final class WorldBootstrap {

    /**
     * Every generated test world starts with this prefix, which is what the sweep matches. Renamed
     * from the upstream harness's {@code cltest_}, so a JECh harness run never shares a save
     * directory with a CloudLib one on the same machine.
     */
    public static final String worldPrefix = "jechtest_";

    /** The fixed seed, so the same world is generated every run. */
    private static final long seed = 4_948_311_507L;

    private static final Logger log = LoggerFactory.getLogger("jechtest-uitest");

    private WorldBootstrap() {}

    /** The world name for a run id. */
    public static String worldName(String runId) {
        return worldPrefix + runId;
    }

    /** Reports which save directories the sweep would remove, for the log. */
    public static List<String> staleWorlds(SaveFormat source) {
        return staleWorlds(source, Collections.emptyList());
    }

    /**
     * Reports which save directories the sweep would remove, keeping the ones this run owns.
     *
     * <p>A run's own saves are the world it is in and the ones it has already created, and they are
     * excluded because the sweep's job is the wrecks of earlier runs: a world this run just left is
     * still being flushed to disk by its own server for a few ticks, and deleting it underneath that
     * write is how a run loses its save — observed as {@code NoSuchFileException} for the temp files
     * the save writes.
     */
    public static List<String> staleWorlds(SaveFormat source, List<String> keep) {
        Path base = source.getBaseDir();
        if (!Files.isDirectory(base)) return Collections.emptyList();
        try (Stream<Path> entries = Files.list(base)) {
            return entries.filter(Files::isDirectory).map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(worldPrefix))
                    .filter(name -> keep.stream().noneMatch(name::startsWith)).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("could not list save directories under {}", base, e);
            return Collections.emptyList();
        }
    }

    /** Deletes every stale save directory, and reports how many went. */
    public static int deleteStaleWorlds(SaveFormat source) {
        return deleteStaleWorlds(source, Collections.emptyList());
    }

    /** Deletes every save directory named after the test prefix, keeping the ones this run owns. */
    public static int deleteStaleWorlds(SaveFormat source, List<String> keep) {
        List<String> stale = staleWorlds(source, keep);
        int removed = 0;
        for (String name : stale) {
            // Port note (1.21.1 → 1.16.5): the getLevelPath(String) overload does not exist here;
            // getBaseDir().resolve(name) is the same child-of-saves path the world list itself
            // computes for a save named `name`.
            Path directory = source.getBaseDir().resolve(name);
            try {
                deleteRecursively(directory);
                removed++;
            } catch (IOException e) {
                log.warn("could not delete the stale test world {}", directory, e);
            }
        }
        if (removed > 0) {
            log.info("swept {} stale test world(s)", removed);
        }
        return removed;
    }

    /**
     * Reopens an existing save — the rejoin half of a {@code reenter} session.
     *
     * <p>The world is not regenerated and nothing about it is reset: the save is opened the way the
     * single-player world list opens it, so blocks, block entities and the pinned rules placed by
     * earlier scenarios are all still there. That is the difference this tier exists for. A save
     * that cannot be read leads to a rejoin that never finishes, and the caller's own budget reports
     * that as the fatal it is — {@code loadLevel(String)} takes no failure callback.
     */
    public static void rejoinLevel(Minecraft minecraft, String worldName) {
        log.info("reopening the test world {} through loadLevel", worldName);
        minecraft.loadLevel(worldName);
    }

    /**
     * Creates and loads a fresh world.
     *
     * <p>This call pumps the render loop internally — it opens a loading screen, which fires frame
     * events of its own — so the caller must set up its state before calling it, never after.
     */
    public static void createFreshLevel(Minecraft minecraft, String worldName) {
        WorldSettings settings = new WorldSettings(
            worldName,
            GameType.CREATIVE,
            false,
            Difficulty.PEACEFUL,
            true,
            new GameRules(),
            DatapackCodec.DEFAULT
        );
        // The registries the level's generators are built from: DynamicRegistries.builtin() is this
        // version's RegistryAccess.builtin() (the 1.18.2-era builtinCopy() does not exist yet).
        DynamicRegistries.Impl registries = DynamicRegistries.builtin();
        Registry<DimensionType> dimensionTypes = registries.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY);
        Registry<Biome> biomes = registries.registryOrThrow(Registry.BIOME_REGISTRY);
        Registry<DimensionSettings> noiseSettings = registries.registryOrThrow(Registry.NOISE_GENERATOR_SETTINGS_REGISTRY);
        FlatGenerationSettings flatSettings = FlatGenerationSettings.getDefault(biomes);
        FlatChunkGenerator flatSource = new FlatChunkGenerator(flatSettings);
        // Port note (1.18.2 → 1.16.5): the level-stem registry has to come from
        // DimensionType.defaultDimensions(..., seed) — the shape the vanilla superflat preset uses
        // itself — and not from DimensionGeneratorSettings.makeDefault(...).dimensions(). makeDefault
        // invents a seed of its own (1.16.5 bytecode: `new Random().nextLong()`, feeding both
        // defaultDimensions and makeDefaultOverworld) and wraps those stems in a settings object
        // carrying *that* seed; keeping those stems while the settings below carry this copy's fixed
        // seed leaves the nether/end stems disagreeing with the world seed, so the run creates the
        // save, brings the integrated server up and never completes the join — no
        // "Time elapsed" / "Preparing spawn area" marker anywhere. defaultDimensions takes this
        // copy's seed, so both halves agree.
        SimpleRegistry<Dimension> dimensions = DimensionGeneratorSettings.withOverworld(
            dimensionTypes,
            DimensionType.defaultDimensions(dimensionTypes, biomes, noiseSettings, seed),
            flatSource
        );
        DimensionGeneratorSettings worldGenSettings = new DimensionGeneratorSettings(seed, false, false, dimensions);
        minecraft.createLevel(worldName, settings, registries, worldGenSettings);
    }

    private static void deleteRecursively(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
