package me.towdium.jecharacters.uitest.engine;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.core.MappedRegistry;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.Collections;

/**
 * Creates the throwaway world a run uses, and sweeps the ones earlier runs left behind.
 *
 * <p>The world is a fixed-seed superflat level in creative with cheats on, so two runs share a
 * backdrop and a scenario can place blocks without any setup of its own. Sweeping stale worlds is
 * not housekeeping: two runs of the same run id would otherwise collide on the save directory, and
 * a leftover locked world is the single biggest source of flakiness in this kind of harness.
 *
 * <p><b>Port note (1.21.1 → 1.16.5).</b> The 1.21.1 copy creates the level through
 * {@code Minecraft.createWorldOpenFlows()} and reopens one through {@code openWorld(name,
 * onFailure)}; neither exists in 1.16.5 — that whole flow lives on {@link Minecraft} itself here.
 * Creation is {@code Minecraft.createLevel(String, LevelSettings, RegistryAccess.RegistryHolder,
 * WorldGenSettings)} (client mappings 1.16.5: {@code Minecraft.1485}) and the flat-level assembly
 * below is this copy's own transcription of the 1.16.5 registries: there is no flat-preset
 * factory, so a normal {@link WorldGenSettings} is built through
 * {@code WorldGenSettings.makeDefault} (which needs the dimension-type, biome and
 * noise-generator-settings registries of a {@code RegistryAccess.builtin()} holder — the
 * 1.18.2-era {@code builtinCopy()} does not exist yet) and its overworld generator is swapped for
 * a {@link FlatLevelSource} through {@code WorldGenSettings.withOverworld}, which on this version
 * takes and returns a {@link MappedRegistry} of level stems. The {@link LevelSettings} tail
 * argument is this version's {@link DataPackConfig} (a 7-argument constructor, the same shape
 * 1.18.2 has). Rejoining is {@code Minecraft.loadLevel(String)} (client mappings 1.16.5:
 * {@code Minecraft.1481}) — the same call the world list's own
 * {@code WorldSelectionList.WorldListEntry.joinWorld()} tail makes, which opens the loading screen
 * itself; it takes no failure callback, and a save that cannot be read is reported by the
 * caller's own rejoin budget.
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

    // 1.16.5 ships log4j2 and no slf4j (slf4j-api entered the game with 1.17), so this
    // line logs through Minecraft's own stack — the same substitution uitest/1.12.2-forge
    // makes. Measured on the resolved compileClasspath; see build.gradle.
    private static final Logger log = LogManager.getLogger("jechtest-uitest");

    private WorldBootstrap() {}

    /** The world name for a run id. */
    public static String worldName(String runId) {
        return worldPrefix + runId;
    }

    /** Reports which save directories the sweep would remove, for the log. */
    public static List<String> staleWorlds(LevelStorageSource source) {
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
    public static List<String> staleWorlds(LevelStorageSource source, List<String> keep) {
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
    public static int deleteStaleWorlds(LevelStorageSource source) {
        return deleteStaleWorlds(source, Collections.emptyList());
    }

    /** Deletes every save directory named after the test prefix, keeping the ones this run owns. */
    public static int deleteStaleWorlds(LevelStorageSource source, List<String> keep) {
        List<String> stale = staleWorlds(source, keep);
        int removed = 0;
        for (String name : stale) {
            // Port note (1.21.1 → 1.16.5): the getLevelPath(String) overload does not exist
            // here; getBaseDir().resolve(name) is the same child-of-saves path the world
            // list itself computes for a save named {@code name}.
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
     * that cannot be read leads to a rejoin that never finishes, and the caller's own budget
     * reports that as the fatal it is — {@code loadLevel(String)} takes no failure callback.
     */
    public static void rejoinLevel(Minecraft minecraft, String worldName) {
        minecraft.loadLevel(worldName);
    }

    /**
     * Creates and loads a fresh world.
     *
     * <p>This call pumps the render loop internally — it opens a loading screen, which fires frame
     * events of its own — so the caller must set up its state before calling it, never after.
     */
    public static void createFreshLevel(Minecraft minecraft, String worldName) {
        LevelSettings settings = new LevelSettings(
            worldName,
            GameType.CREATIVE,
            false,
            Difficulty.PEACEFUL,
            true,
            new GameRules(),
            DataPackConfig.DEFAULT
        );
        RegistryAccess.RegistryHolder registryHolder = RegistryAccess.builtin();
        Registry<DimensionType> dimensionTypes = registryHolder.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY);
        Registry<Biome> biomes = registryHolder.registryOrThrow(Registry.BIOME_REGISTRY);
        Registry<NoiseGeneratorSettings> noiseSettings = registryHolder.registryOrThrow(Registry.NOISE_GENERATOR_SETTINGS_REGISTRY);
        FlatLevelGeneratorSettings flatSettings = FlatLevelGeneratorSettings.getDefault(biomes);
        FlatLevelSource flatSource = new FlatLevelSource(flatSettings);
        WorldGenSettings normalSettings = WorldGenSettings.makeDefault(dimensionTypes, biomes, noiseSettings);
        MappedRegistry<LevelStem> dimensions = WorldGenSettings.withOverworld(dimensionTypes, normalSettings.dimensions(), flatSource);
        WorldGenSettings worldGenSettings = new WorldGenSettings(
            seed,
            normalSettings.generateFeatures(),
            normalSettings.generateBonusChest(),
            dimensions
        );
        minecraft.createLevel(worldName, settings, registryHolder, worldGenSettings);
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
