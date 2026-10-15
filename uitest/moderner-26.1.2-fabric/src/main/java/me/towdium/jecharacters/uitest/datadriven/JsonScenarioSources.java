package me.towdium.jecharacters.uitest.datadriven;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Finding the JSON scenario files, and reading them.
 *
 * <p>Two channels, both of them fail-closed:
 *
 * <ol>
 *   <li><b>Inside a mod</b> — {@code uitest/scenarios/*.json} in any loaded mod's own resources, read
 *       through Fabric Loader's {@code ModContainer.findPath}, with the runtime class path as a
 *       second look so a dev run whose resources are served as plain directories still works. This is
 *       how the harness ships its scenarios in the repository, and how a downstream mod would ship
 *       its own.</li>
 *   <li><b>In the game directory</b> — {@code <game dir>/jech-scenarios/*.json}, re-read on every
 *       scan, so a scenario can be written and tried without touching a checkout.</li>
 * </ol>
 *
 * <p><b>Port note (NeoForge → Fabric).</b> The NeoForge copy walks each mod's contents through FML's
 * {@code IModFile.getContents().visitContent}, whose {@code JarResource} has to be read inside the
 * visit. Fabric Loader's {@code ModContainer.findPath} hands back a real {@link Path} into the mod —
 * a directory in a dev run, an entry of the mod jar's own file system otherwise — so the same folder
 * is reached with one call and read with the ordinary file reader below. Nothing else differs.
 *
 * <p>Nothing here throws. A file that cannot be read is returned as a {@link Loaded} failure and
 * becomes a scenario that fails when it runs, because a file that is present and unreadable is a
 * result, not an absence.
 */
public final class JsonScenarioSources {

    /** One file's outcome: the parsed scenario, or why it could not be read. */
    public record Loaded(String name, String source, @Nullable JsonScenario scenario, @Nullable String failure) {

        /** Whether this file was rejected. */
        public boolean failed() {
            return scenario == null;
        }
    }

    private static final Logger log = LoggerFactory.getLogger("jechtest-uitest");

    private static final String suffix = ".json";

    private JsonScenarioSources() {}

    /** Every JSON scenario the two channels offer, in channel order. */
    public static List<Loaded> loadAll() {
        List<Loaded> loaded = new ArrayList<>();
        loaded.addAll(loadModResources());
        loaded.addAll(loadHotFolder());
        return loaded;
    }

    // region mod resources

    private static List<Loaded> loadModResources() {
        Map<String, Loaded> byName = new LinkedHashMap<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            readInMod(mod).forEach(byName::putIfAbsent);
        }
        for (Path path : findOnClassPath()) {
            for (Path file : jsonFiles(path, "classpath " + path)) {
                byName.putIfAbsent(file.getFileName().toString(), read(file, "classpath " + path));
            }
        }
        return new ArrayList<>(byName.values());
    }

    /**
     * The {@code uitest/scenarios} folder inside one mod, as {@code file name → outcome}.
     *
     * <p>{@code ModContainer.findPath} answers with the path of a file inside the mod — a directory in
     * a dev run, an entry of the mod jar's own file system when the mod is a jar — and is empty when
     * the mod does not carry the folder, which is the common case: only the harness mod and a mod that
     * ships its own scenarios have one. Nothing here throws: a mod whose contents cannot be walked is
     * logged and offers no scenarios.
     */
    private static Map<String, Loaded> readInMod(ModContainer mod) {
        String modId = mod.getMetadata().getId();
        Map<String, Loaded> byName = new LinkedHashMap<>();
        try {
            Path folder = mod.findPath(DataDrivenScenario.resourceFolder).orElse(null);
            for (Path file : jsonFiles(folder, "mod " + modId)) {
                String name = file.getFileName().toString();
                byName.putIfAbsent(name, read(file, "mod " + modId + ":" + name));
            }
        } catch (RuntimeException e) {
            log.warn("could not look for JSON scenarios inside mod {}", modId, e);
        }
        return byName;
    }

    /** The same folder seen through the runtime class path, for a dev run that serves it as a directory. */
    private static List<Path> findOnClassPath() {
        List<Path> found = new ArrayList<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = JsonScenarioSources.class.getClassLoader();
        }
        try {
            Enumeration<URL> urls = loader.getResources(DataDrivenScenario.resourceFolder);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                if ("file".equals(url.getProtocol())) {
                    found.add(Path.of(url.getPath()));
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("could not enumerate {} on the class path", DataDrivenScenario.resourceFolder, e);
        }
        return found;
    }

    // endregion

    // region the hot folder

    private static List<Loaded> loadHotFolder() {
        Path directory = DataDrivenScenario.hotDirectory();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Loaded> loaded = new ArrayList<>();
        for (Path file : jsonFiles(directory, "hot")) {
            loaded.add(read(file, DataDrivenScenario.hotFolder));
        }
        log.info("hot scenario folder {} offered {} file(s)", directory, loaded.size());
        return loaded;
    }

    // endregion

    private static List<Path> jsonFiles(Path directory, String source) {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        } catch (IOException | RuntimeException e) {
            log.warn("could not list the JSON scenarios in {} ({})", directory, source, e);
            return List.of();
        }
    }

    /** Reads one file, turning every failure into a {@link Loaded} the registry can register. */
    private static Loaded read(Path file, String source) {
        String label = source + ":" + file.getFileName();
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException | RuntimeException e) {
            return new Loaded(DataDrivenScenario.fallbackName(file), label, null, unreadable(e));
        }
        return parse(text, DataDrivenScenario.fallbackName(file), label);
    }

    private static String unreadable(Exception e) {
        return "could not be read (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
    }

    /** Parses one scenario file's text, turning every failure into a {@link Loaded}. */
    private static Loaded parse(String text, String fallbackName, String label) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(text);
        } catch (RuntimeException e) {
            return new Loaded(fallbackName, label, null, "is not valid JSON (" + e.getMessage() + ")");
        }
        if (!parsed.isJsonObject()) {
            return new Loaded(fallbackName, label, null,
                    "must hold one JSON object, not " + parsed.getClass().getSimpleName());
        }
        JsonObject root = parsed.getAsJsonObject();
        try {
            JsonScenario scenario = JsonScenarioReader.parse(root, label);
            return new Loaded(scenario.name(), label, scenario, null);
        } catch (ScenarioJsonException e) {
            String name = root.has("name") && root.get("name").isJsonPrimitive()
                    ? root.get("name").getAsString()
                    : fallbackName;
            return new Loaded(name, label, null, e.getMessage());
        }
    }
}
