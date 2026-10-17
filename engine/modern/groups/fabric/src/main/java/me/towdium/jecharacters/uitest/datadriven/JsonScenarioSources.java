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

public final class JsonScenarioSources {

    public record Loaded(String name, String source, @Nullable JsonScenario scenario, @Nullable String failure) {

        public boolean failed() {
            return scenario == null;
        }
    }

    private static final Logger log = LoggerFactory.getLogger("jechtest-uitest");

    private static final String suffix = ".json";

    private JsonScenarioSources() {}

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
