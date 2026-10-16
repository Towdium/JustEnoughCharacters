package me.towdium.jecharacters.uitest.datadriven;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JsonScenarioSources {

    public static final class Loaded {

        private final String name;
        private final String source;
        private final JsonScenario scenario;
        private final String failure;

        Loaded(String name, String source, JsonScenario scenario, String failure) {
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

        public JsonScenario scenario() {
            return scenario;
        }

        public String failure() {
            return failure;
        }

        public boolean failed() {
            return scenario == null;
        }
    }

    private static final Logger log = LogManager.getLogger("jechtest-uitest");

    private static final String suffix = ".json";

    private JsonScenarioSources() {}

    public static List<Loaded> loadAll() {
        List<Loaded> loaded = new ArrayList<Loaded>();
        loaded.addAll(loadModResources());
        loaded.addAll(loadHotFolder());
        return loaded;
    }

    // region mod resources

    private static List<Loaded> loadModResources() {
        Map<String, Path> roots = new LinkedHashMap<String, Path>();
        for (ModContainer mod : Loader.instance().getModList()) {
            Path fromFml = findInMod(mod);
            if (fromFml != null) {
                roots.put("mod " + mod.getModId(), fromFml);
            }
        }
        for (Path path : findOnClassPath()) {
            roots.putIfAbsent("classpath " + path, path);
        }
        Map<String, Loaded> byName = new LinkedHashMap<String, Loaded>();
        for (Map.Entry<String, Path> entry : roots.entrySet()) {
            for (Path file : jsonFiles(entry.getValue(), entry.getKey())) {
                if (!byName.containsKey(file.getFileName().toString())) {
                    byName.put(file.getFileName().toString(), read(file, entry.getKey()));
                }
            }
        }
        return new ArrayList<Loaded>(byName.values());
    }

    private static Path findInMod(ModContainer mod) {
        File source;
        try {
            source = mod.getSource();
        } catch (RuntimeException e) {
            log.warn("could not read the source of mod {} ({})", mod.getModId(), e);
            return null;
        }
        if (source == null) return null;
        File folder = new File(source, DataDrivenScenario.resourceFolder);
        return folder.isDirectory() ? folder.toPath() : null;
    }

    private static List<Path> findOnClassPath() {
        List<Path> found = new ArrayList<Path>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = JsonScenarioSources.class.getClassLoader();
        }
        try {
            Enumeration<URL> urls = loader.getResources(DataDrivenScenario.resourceFolder);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                if ("file".equals(url.getProtocol())) {
                    found.add(Paths.get(url.toURI()));
                }
            }
        } catch (IOException | URISyntaxException | RuntimeException e) {
            log.warn("could not enumerate {} on the class path", DataDrivenScenario.resourceFolder, e);
        }
        return found;
    }

    // endregion

    // region the hot folder

    private static List<Loaded> loadHotFolder() {
        Path directory = DataDrivenScenario.hotDirectory().toPath();
        if (!Files.isDirectory(directory)) {
            return new ArrayList<Loaded>();
        }
        List<Loaded> loaded = new ArrayList<Loaded>();
        for (Path file : jsonFiles(directory, "hot")) {
            loaded.add(read(file, DataDrivenScenario.hotFolder));
        }
        log.info("hot scenario folder {} offered {} file(s)", directory, loaded.size());
        return loaded;
    }

    // endregion

    private static List<Path> jsonFiles(Path directory, String source) {
        if (directory == null || !Files.isDirectory(directory)) {
            return new ArrayList<Path>();
        }
        try {
            Stream<Path> entries = Files.list(directory);
            try {
                return entries
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(suffix))
                        .sorted()
                        .collect(Collectors.toList());
            } finally {
                entries.close();
            }
        } catch (IOException | RuntimeException e) {
            log.warn("could not list the JSON scenarios in {} ({})", directory, source, e);
            return new ArrayList<Path>();
        }
    }

    private static Loaded read(Path file, String source) {
        String label = source + ":" + file.getFileName();
        String text;
        try {
            text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return new Loaded(DataDrivenScenario.fallbackName(file.toFile()), label, null,
                    "could not be read (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }
        JsonElement parsed;
        try {
            parsed = new JsonParser().parse(text);
        } catch (RuntimeException e) {
            return new Loaded(DataDrivenScenario.fallbackName(file.toFile()), label, null,
                    "is not valid JSON (" + e.getMessage() + ")");
        }
        if (!parsed.isJsonObject()) {
            return new Loaded(DataDrivenScenario.fallbackName(file.toFile()), label, null,
                    "must hold one JSON object, not " + parsed.getClass().getSimpleName());
        }
        JsonObject root = parsed.getAsJsonObject();
        try {
            JsonScenario scenario = JsonScenarioReader.parse(root, label);
            return new Loaded(scenario.name(), label, scenario, null);
        } catch (ScenarioJsonException e) {
            String name = root.has("name") && root.get("name").isJsonPrimitive()
                    ? root.get("name").getAsString()
                    : DataDrivenScenario.fallbackName(file.toFile());
            return new Loaded(name, label, null, e.getMessage());
        }
    }
}
