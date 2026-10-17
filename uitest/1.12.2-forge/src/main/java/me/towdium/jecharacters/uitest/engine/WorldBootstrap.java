package me.towdium.jecharacters.uitest.engine;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.FlatGeneratorInfo;
import net.minecraft.world.gen.FlatLayerInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WorldBootstrap {

    public static final String worldPrefix = "jechtest_";

    private static final long seed = 4_948_311_507L;

    private static final Logger log = LogManager.getLogger("jechtest-uitest");

    private WorldBootstrap() {}

    public static String worldName(String runId) {
        return worldPrefix + runId;
    }

    public static File savesDir(Minecraft minecraft) {
        return new File(minecraft.mcDataDir, "saves");
    }

    public static List<String> staleWorlds(File saves) {
        return staleWorlds(saves, Collections.<String>emptyList());
    }

    public static List<String> staleWorlds(File saves, List<String> keep) {
        if (saves == null || !saves.isDirectory()) return Collections.emptyList();
        File[] entries = saves.listFiles();
        if (entries == null) return Collections.emptyList();
        List<String> stale = new ArrayList<String>();
        for (File entry : entries) {
            if (!entry.isDirectory()) continue;
            String name = entry.getName();
            if (!name.startsWith(worldPrefix)) continue;
            boolean owned = false;
            for (String kept : keep) {
                if (name.startsWith(kept)) {
                    owned = true;
                    break;
                }
            }
            if (!owned) stale.add(name);
        }
        Collections.sort(stale);
        return stale;
    }

    public static int deleteStaleWorlds(File saves, List<String> keep) {
        int removed = 0;
        for (String name : staleWorlds(saves, keep)) {
            File directory = new File(saves, name);
            try {
                deleteRecursively(directory.toPath());
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

    public static void createFreshWorld(Minecraft minecraft, String folderName, String worldName) {
        minecraft.launchIntegratedServer(folderName, worldName, settings());
    }

    public static WorldSettings settings() {
        WorldSettings settings = new WorldSettings(seed, GameType.CREATIVE, false, false, WorldType.FLAT);
        settings.setGeneratorOptions(generatorOptions());
        settings.enableCommands();
        return settings;
    }

    private static String generatorOptions() {
        FlatGeneratorInfo info = new FlatGeneratorInfo();
        info.setBiome(Biome.getIdForBiome(Biomes.VOID));
        info.getFlatLayers().add(new FlatLayerInfo(3, Blocks.AIR));
        info.getFlatLayers().add(new FlatLayerInfo(1, Blocks.STONE));
        info.updateLayers();
        return info.toString();
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
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
