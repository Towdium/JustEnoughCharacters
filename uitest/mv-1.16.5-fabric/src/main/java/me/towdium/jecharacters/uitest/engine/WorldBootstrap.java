package me.towdium.jecharacters.uitest.engine;

import me.towdium.jecharacters.uitest.api.RunConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.core.MappedRegistry;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.StructureSettings;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.Collections;

public final class WorldBootstrap {

    public static final String worldPrefix = "jechtest_";

    private static final long seed = 4_948_311_507L;

    private static final Logger log = LogManager.getLogger("jechtest-uitest");

    private WorldBootstrap() {}

    public static String worldName(String runId) {
        return worldPrefix + runId;
    }

    public static List<String> staleWorlds(LevelStorageSource source) {
        return staleWorlds(source, Collections.emptyList());
    }

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

    public static int deleteStaleWorlds(LevelStorageSource source) {
        return deleteStaleWorlds(source, Collections.emptyList());
    }

    public static int deleteStaleWorlds(LevelStorageSource source, List<String> keep) {
        List<String> stale = staleWorlds(source, keep);
        int removed = 0;
        for (String name : stale) {
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

    public static void rejoinLevel(Minecraft minecraft, String worldName) {
        minecraft.loadLevel(worldName);
    }

    public static void createFreshLevel(Minecraft minecraft, String worldName, RunConfig config) {
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
        if (!"flat".equals(config.worldGenerator())) {
            List<FlatLayerInfo> layers = new ArrayList<>();
            layers.add(new FlatLayerInfo(config.platformLayers(), Blocks.STONE));
            flatSettings = new FlatLevelGeneratorSettings(
                biomes,
                new StructureSettings(Optional.empty(), Collections.emptyMap()),
                layers,
                false,
                false,
                Optional.of(() -> biomes.getOrThrow(Biomes.THE_VOID))
            );
        }
        FlatLevelSource flatSource = new FlatLevelSource(flatSettings);
        MappedRegistry<LevelStem> dimensions = WorldGenSettings.withOverworld(
            dimensionTypes,
            DimensionType.defaultDimensions(dimensionTypes, biomes, noiseSettings, seed),
            flatSource
        );
        WorldGenSettings worldGenSettings = new WorldGenSettings(seed, false, false, dimensions);
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
