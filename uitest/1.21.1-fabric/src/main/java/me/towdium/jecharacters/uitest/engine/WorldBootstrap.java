package me.towdium.jecharacters.uitest.engine;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
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

/**
 * Creates the throwaway world a run uses, and sweeps the ones earlier runs left behind.
 *
 * <p>The world is a fixed-seed superflat level in creative with cheats on, so two runs share a
 * backdrop and a scenario can place blocks without any setup of its own. Sweeping stale worlds is
 * not housekeeping: two runs of the same run id would otherwise collide on the save directory, and
 * a leftover locked world is the single biggest source of flakiness in this kind of harness.
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
    public static List<String> staleWorlds(LevelStorageSource source) {
        return staleWorlds(source, List.of());
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
        if (!Files.isDirectory(base)) return List.of();
        try (Stream<Path> entries = Files.list(base)) {
            return entries.filter(Files::isDirectory).map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(worldPrefix))
                    .filter(name -> keep.stream().noneMatch(name::startsWith)).sorted().toList();
        } catch (IOException e) {
            log.warn("could not list save directories under {}", base, e);
            return List.of();
        }
    }

    /** Deletes every stale save directory, and reports how many went. */
    public static int deleteStaleWorlds(LevelStorageSource source) {
        return deleteStaleWorlds(source, List.of());
    }

    /** Deletes every save directory named after the test prefix, keeping the ones this run owns. */
    public static int deleteStaleWorlds(LevelStorageSource source, List<String> keep) {
        List<String> stale = staleWorlds(source, keep);
        int removed = 0;
        for (String name : stale) {
            Path directory = source.getLevelPath(name);
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
     * earlier scenarios are all still there. That is the difference this tier exists for.
     *
     * <p>The failure callback only logs: a save that cannot be read leads to a rejoin that never
     * finishes, and the caller's own budget reports that as the fatal it is.
     */
    public static void rejoinLevel(Minecraft minecraft, String worldName) {
        minecraft.createWorldOpenFlows().openWorld(
            worldName,
            () -> log.error("could not reopen the test world {}; the run will report the rejoin timing out", worldName)
        );
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
            WorldDataConfiguration.DEFAULT
        );
        WorldOptions options = new WorldOptions(seed, false, false);
        minecraft.createWorldOpenFlows()
                .createFreshLevel(worldName, settings, options, WorldBootstrap::flatDimensions, minecraft.screen);
    }

    /** The flat world preset's dimensions, generated from the datapack registry. */
    private static WorldDimensions flatDimensions(RegistryAccess registry) {
        return registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                .createWorldDimensions();
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
