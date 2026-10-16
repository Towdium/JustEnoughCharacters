package me.towdium.jecharacters.uitest.datadriven;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
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
        Map<String, Path> roots = new LinkedHashMap<>();
        for (IModInfo mod : ModList.get().getMods()) {
            Path fromFml = findInMod(mod.getModId());
            if (fromFml != null) {
                roots.put("mod " + mod.getModId(), fromFml);
            }
        }
        for (Path path : findOnClassPath()) {
            roots.putIfAbsent("classpath " + path, path);
        }
        Map<String, Loaded> byName = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : roots.entrySet()) {
            for (Path file : jsonFiles(entry.getValue(), entry.getKey())) {
                byName.putIfAbsent(file.getFileName().toString(), read(file, entry.getKey()));
            }
        }
        return new ArrayList<>(byName.values());
    }

    private static @Nullable Path findInMod(String modId) {
        IModFileInfo fileInfo = ModList.get().getModFileById(modId);
        if (fileInfo == null) return null;
        IModFile file = fileInfo.getFile();
        try {
            return file.findResource("uitest", "scenarios");
        } catch (RuntimeException e) {
            log.warn("could not look for JSON scenarios inside mod {} ({})", modId, file.getFileName(), e);
            return null;
        }
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
            return new Loaded(DataDrivenScenario.fallbackName(file), label, null,
                    "could not be read (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(text);
        } catch (RuntimeException e) {
            return new Loaded(DataDrivenScenario.fallbackName(file), label, null,
                    "is not valid JSON (" + e.getMessage() + ")");
        }
        if (!parsed.isJsonObject()) {
            return new Loaded(DataDrivenScenario.fallbackName(file), label, null,
                    "must hold one JSON object, not " + parsed.getClass().getSimpleName());
        }
        JsonObject root = parsed.getAsJsonObject();
        try {
            JsonScenario scenario = JsonScenarioReader.parse(root, label);
            return new Loaded(scenario.name(), label, scenario, null);
        } catch (ScenarioJsonException e) {
            String name = root.has("name") && root.get("name").isJsonPrimitive()
                    ? root.get("name").getAsString()
                    : DataDrivenScenario.fallbackName(file);
            return new Loaded(name, label, null, e.getMessage());
        }
    }
}
