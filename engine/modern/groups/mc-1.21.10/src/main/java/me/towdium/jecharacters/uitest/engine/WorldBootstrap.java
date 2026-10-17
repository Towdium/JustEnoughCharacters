package me.towdium.jecharacters.uitest.engine;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
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

public final class WorldBootstrap {

    public static final String worldPrefix = "jechtest_";

    private static final long seed = 4_948_311_507L;

    private static final Logger log = LoggerFactory.getLogger("jechtest-uitest");

    private WorldBootstrap() {}

    public static String worldName(String runId) {
        return worldPrefix + runId;
    }

    public static List<String> staleWorlds(LevelStorageSource source) {
        return staleWorlds(source, List.of());
    }

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

    public static int deleteStaleWorlds(LevelStorageSource source) {
        return deleteStaleWorlds(source, List.of());
    }

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

    public static void rejoinLevel(Minecraft minecraft, String worldName) {
        minecraft.createWorldOpenFlows().openWorld(
            worldName,
            () -> log.error("could not reopen the test world {}; the run will report the rejoin timing out", worldName)
        );
    }

    public static void createFreshLevel(Minecraft minecraft, String worldName) {
        LevelSettings settings = new LevelSettings(
            worldName,
            GameType.CREATIVE,
            false,
            Difficulty.PEACEFUL,
            true,
            new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()),
            WorldDataConfiguration.DEFAULT
        );
        WorldOptions options = new WorldOptions(seed, false, false);
        minecraft.createWorldOpenFlows()
                .createFreshLevel(worldName, settings, options, WorldBootstrap::flatDimensions, minecraft.screen);
    }

    private static WorldDimensions flatDimensions(HolderLookup.Provider registry) {
        return registry.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value()
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
