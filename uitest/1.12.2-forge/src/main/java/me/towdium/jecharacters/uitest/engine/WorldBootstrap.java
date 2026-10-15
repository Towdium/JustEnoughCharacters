package me.towdium.jecharacters.uitest.engine;

import net.minecraft.client.Minecraft;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
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

/**
 * Creates the throwaway world a run uses, and sweeps the ones earlier runs left behind.
 *
 * <p>The world is a fixed-seed superflat level in creative with cheats on, so two runs share a
 * backdrop and a scenario can place blocks without any setup of its own. Sweeping stale worlds is not
 * housekeeping: two runs of the same run id would otherwise collide on the save directory, and a
 * leftover locked world is the single biggest source of flakiness in this kind of harness.
 *
 * <p><b>Port note (this is the one place the 1.12.2 port changes an engine decision rather than a
 * spelling).</b> On 1.21.1 world creation is a call into {@code Minecraft.createWorldOpenFlows()}
 * which the runner makes from the client thread and which pumps the render loop internally, so the
 * frame clock and the phase machine keep running. On 1.12.2 there is no such API: the only entry
 * point is {@link Minecraft#launchIntegratedServer(String, String, WorldSettings)}, and it is
 * <em>blocking</em> — after starting the server it spins in
 * {@code while (!integratedServer.serverIsInRunLoop()) Thread.sleep(200)} (Minecraft.java:2514-2538),
 * then opens the local network connection and returns.
 *
 * <p>That blocking call has to run on the client thread. The previous wave's reading suggested a
 * worker thread so the frame pump could keep turning; a worker was tried first in this wave and is
 * impossible — the launcher drives the client's loading screen, {@code LoadingScreenRenderer} calls
 * into GL, and a thread that never made the context current dies with "No OpenGL context found in the
 * current thread" ({@code GlStateManager.clear}, measured on the first boot). So the launch runs at a
 * tick boundary on the client thread (see {@code UITestRunner#createWorld()}), the frame clock stalls
 * for the length of the load, and the watchdog is what tells a slow load from a hung one.
 *
 * <p>Three more deltas from the 1.21.1 copy: the saves directory is
 * {@code <mcDataDir>/saves} rather than a {@code LevelStorageSource} (1.12.2's
 * {@code AnvilSaveConverter} exposes no base-dir accessor, and the directory is what the sweep
 * needs); the world is not rejoined, because the session tiers are a later wave's; and the flat
 * preset is 1.12.2's own {@code WorldType.FLAT} with the generator options left empty, which is
 * 1.12.2's "Classic Flat" — bedrock, two dirt, one grass — rather than a datapack-registry preset.
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

    private static final Logger log = LogManager.getLogger("jechtest-uitest");

    private WorldBootstrap() {}

    /** The world name for a run id. */
    public static String worldName(String runId) {
        return worldPrefix + runId;
    }

    /** The directory every 1.12.2 save lives in. */
    public static File savesDir(Minecraft minecraft) {
        return new File(minecraft.mcDataDir, "saves");
    }

    /** Reports which save directories the sweep would remove. */
    public static List<String> staleWorlds(File saves) {
        return staleWorlds(saves, Collections.<String>emptyList());
    }

    /**
     * Reports which save directories the sweep would remove, keeping the ones this run owns.
     *
     * <p>A run's own saves are the world it is in and the ones it has already created, and they are
     * excluded because the sweep's job is the wrecks of earlier runs: a world this run just left is
     * still being flushed to disk by its own server for a few ticks, and deleting it underneath that
     * write is how a run loses its save.
     */
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

    /** Deletes every stale save directory, and reports how many went. */
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

    /**
     * Creates and loads a fresh world.
     *
     * <p>Blocking, and must be called on the client thread — see the class note for why.
     *
     * @param folderName the save directory name
     * @param worldName the in-game display name
     */
    public static void createFreshWorld(Minecraft minecraft, String folderName, String worldName) {
        minecraft.launchIntegratedServer(folderName, worldName, settings());
    }

    /** The throwaway world's settings: superflat, creative, cheats on, fixed seed. */
    public static WorldSettings settings() {
        WorldSettings settings = new WorldSettings(seed, GameType.CREATIVE, true, false, WorldType.FLAT);
        settings.enableCommands();
        return settings;
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
