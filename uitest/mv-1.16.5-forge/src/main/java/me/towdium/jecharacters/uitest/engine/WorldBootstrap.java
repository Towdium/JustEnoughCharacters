package me.towdium.jecharacters.uitest.engine;

import me.towdium.jecharacters.uitest.api.RunConfig;
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
import net.minecraft.world.biome.Biomes;
import net.minecraft.block.Blocks;
import net.minecraft.world.gen.DimensionSettings;
import net.minecraft.world.gen.FlatChunkGenerator;
import net.minecraft.world.gen.FlatGenerationSettings;
import net.minecraft.world.gen.FlatLayerInfo;
import net.minecraft.world.gen.settings.DimensionGeneratorSettings;
import net.minecraft.world.gen.settings.DimensionStructuresSettings;
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
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.Collections;
import java.util.stream.Collectors;

public final class WorldBootstrap {

    public static final String worldPrefix = "jechtest_";

    private static final long seed = 4_948_311_507L;

    private static final Logger log = LoggerFactory.getLogger("jechtest-uitest");

    private WorldBootstrap() {}

    public static String worldName(String runId) {
        return worldPrefix + runId;
    }

    public static List<String> staleWorlds(SaveFormat source) {
        return staleWorlds(source, Collections.emptyList());
    }

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

    public static int deleteStaleWorlds(SaveFormat source) {
        return deleteStaleWorlds(source, Collections.emptyList());
    }

    public static int deleteStaleWorlds(SaveFormat source, List<String> keep) {
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
        log.info("reopening the test world {} through loadLevel", worldName);
        minecraft.loadLevel(worldName);
    }

    public static void createFreshLevel(Minecraft minecraft, String worldName, RunConfig config) {
        WorldSettings settings = new WorldSettings(
            worldName,
            GameType.CREATIVE,
            false,
            Difficulty.PEACEFUL,
            true,
            new GameRules(),
            DatapackCodec.DEFAULT
        );
        DynamicRegistries.Impl registries = DynamicRegistries.builtin();
        Registry<DimensionType> dimensionTypes = registries.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY);
        Registry<Biome> biomes = registries.registryOrThrow(Registry.BIOME_REGISTRY);
        Registry<DimensionSettings> noiseSettings = registries.registryOrThrow(Registry.NOISE_GENERATOR_SETTINGS_REGISTRY);
        FlatGenerationSettings flatSettings = FlatGenerationSettings.getDefault(biomes);
        if (!"flat".equals(config.worldGenerator())) {
            List<FlatLayerInfo> layers = new ArrayList<>();
            layers.add(new FlatLayerInfo(config.platformLayers(), Blocks.STONE));
            flatSettings = new FlatGenerationSettings(
                biomes,
                new DimensionStructuresSettings(Optional.empty(), Collections.emptyMap()),
                layers,
                false,
                false,
                Optional.of(() -> biomes.getOrThrow(Biomes.THE_VOID))
            );
        }
        FlatChunkGenerator flatSource = new FlatChunkGenerator(flatSettings);
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
