package me.towdium.jecharacters.uitest.datadriven;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.jspecify.annotations.Nullable;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

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
import java.util.stream.Collectors;
import java.util.Collections;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

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
 * is reached with one call and read with the ordinary file reader below. Nothing else differs. The
 * nested {@code Loaded} record is a plain immutable class — Java 8 has no records, so the fields are
 * final, the accessors keep the record components' names and the constructor is the canonical one;
 * nothing else changes.
 *
 * <p>Nothing here throws. A file that cannot be read is returned as a {@link Loaded} failure and
 * becomes a scenario that fails when it runs, because a file that is present and unreadable is a
 * result, not an absence.
 */
public final class JsonScenarioSources {

    /** One file's outcome: the parsed scenario, or why it could not be read. */
    public static final class Loaded {

        private final String name;
        private final String source;
        @Nullable
        private final JsonScenario scenario;
        @Nullable
        private final String failure;

        public Loaded(
            String name,
            String source,
            @Nullable JsonScenario scenario,
            @Nullable String failure) {
            this.name = name;
            this.source = source;
            this.scenario = scenario;
            this.failure = failure;
        }

        public String name() {
            return name;
        }

        public String source() {
            return source;
        }

        /** The parsed scenario, or {@code null} when the file was rejected. */
        public JsonScenario scenario() {
            return scenario;
        }

        /** Why the file was rejected, or {@code null} when it was read. */
        public String failure() {
            return failure;
        }

        /** Whether this file was rejected. */
        public boolean failed() {
            return scenario == null;
        }
    }

    // 1.16.5 ships log4j2 and no slf4j (slf4j-api entered the game with 1.17), so this
    // line logs through Minecraft's own stack — the same substitution uitest/1.12.2-forge
    // makes. Measured on the resolved compileClasspath; see build.gradle.
    private static final Logger log = LogManager.getLogger("jechtest-uitest");

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
                    found.add(Paths.get(url.getPath()));
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
            return Collections.emptyList();
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
            return Collections.emptyList();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException | RuntimeException e) {
            log.warn("could not list the JSON scenarios in {} ({})", directory, source, e);
            return Collections.emptyList();
        }
    }

    /** Reads one file, turning every failure into a {@link Loaded} the registry can register. */
    private static Loaded read(Path file, String source) {
        String label = source + ":" + file.getFileName();
        String text;
        try {
            text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
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
            parsed = new JsonParser().parse(text);
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
