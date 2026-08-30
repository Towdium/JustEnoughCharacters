package dev.vfyjxf.gradle;

import com.google.gson.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

public abstract class GenerateTargetConfigTask extends DefaultTask {

    private static final String TARGETS_JSON = "targets.json";
    private static final String FLUID_STACK = "Lnet/minecraftforge/fluids/FluidStack";

    @InputFile
    public abstract RegularFileProperty getMappingsFile();

    @InputFiles
    public abstract ConfigurableFileCollection getTargetConfig();

    @Input
    public abstract Property<String> getSuffixClassName();

    @OutputFile
    public abstract RegularFileProperty getConfigFile();


    @TaskAction
    public void generateConfig() {
        Set<File> configFiles = getTargetConfig().getFiles();
        File mappingFile = getMappingsFile().get().getAsFile();
        Yaml yaml = new Yaml();
        Map<String, Set<String>> allTypedTargets = new HashMap<>();
        try {
            Map<String, List<Map<String, String>>> mappings = mappingFile.exists() && mappingFile.isFile() ?
                    yaml.load(Files.readString(mappingFile.toPath())) : new HashMap<>();
            var project = getProject();
            Path targetsJsonPath = getConfigFile().getAsFile().get().toPath();
            boolean fabric = project.getName().contains("fabric");
            // Forge 1.16.5 ships in the MCP namespace, other versions keep Mojang class names.
            boolean mcp = !fabric && project.getPath().contains("1.16.5");

            //region read all generator config files
            for (File file : configFiles) {
                if (file == null || !file.isFile()) continue;
                String extension = getFileExtension(file.getName());
                if (!extension.equals("yaml")) continue;

                try {
                    Map<String, List<String>> typedConfig = yaml.load(Files.readString(file.toPath()));
                    for (var entry : typedConfig.entrySet()) {
                        var type = entry.getKey();
                        var targets = entry.getValue()
                                .stream()
                                .filter(this::checkTargetFormat)
                                .distinct()
                                .toList();
                        allTypedTargets.computeIfAbsent(type, k -> new TreeSet<>()).addAll(targets);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
            //endregion

            //region map yaml to json
            JsonObject targetsJson = new JsonObject();

            for (var entry : allTypedTargets.entrySet()) {
                var type = entry.getKey();
                var targets = entry.getValue();
                var typedTargetArray = new JsonArray();
                if (fabric) {
                    targets = targets.stream()
                            .filter(GenerateTargetConfigTask::notContainsFluidStack)
                            .map(mapped -> remap(mapped, mappings, "intermediary"))
                            .collect(Collectors.toSet());
                } else if (mcp) {
                    targets = targets.stream()
                            .map(mapped -> remap(mapped, mappings, "mcp"))
                            .collect(Collectors.toSet());
                }
                targets.forEach(typedTargetArray::add);
                var typedJson = new JsonObject();
                typedJson.add("default", typedTargetArray);
                typedJson.add("additional", new JsonArray());
                targetsJson.add(type, typedJson);
            }
            targetsJson.add("removals", new JsonArray());
            targetsJson.add("suffixClassName", new JsonPrimitive(getSuffixClassName().get()));

            //endregion
            //region write to file
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();
            String jsonStr = gson.toJson(targetsJson);
            File jsonFile = targetsJsonPath.toFile();
            if (jsonFile.exists()) {
                if (!jsonFile.delete()) {
                    throw new RuntimeException("Failed to delete existing targets.json file: " + jsonFile.getAbsolutePath());
                }
            }
            Files.createDirectories(targetsJsonPath.getParent());
            Files.writeString(targetsJsonPath,
                    jsonStr,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE
            );
            try {
                JsonParser.parseString(Files.readString(targetsJsonPath));
            } catch (JsonSyntaxException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String remap(String target, Map<String, List<Map<String, String>>> mappings, String namespace) {
        for (var mappingEntry : mappings.entrySet()) {
            if (target.contains(mappingEntry.getKey())) {
                String mapped = getMapping(namespace, mappingEntry.getValue());
                if (mapped != null) target = target.replace(mappingEntry.getKey(), mapped);
            }
        }
        return target;
    }

    private static String getMapping(String namespace, List<Map<String, String>> list) {
        return list.stream()
                .filter(mapping -> mapping.containsKey(namespace))
                .findFirst()
                .map(mapping -> mapping.get(namespace))
                .orElse(null);
    }

    private static String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex == -1 ? "" : fileName.substring(dotIndex + 1);
    }

    private boolean checkTargetFormat(String target) {
        Logger logger = getProject().getLogger();
        String[] split = target.split(":");
        if (split.length != 2) {
            logger.error("Invalid target: \"{}\", expected format is namespace:name", target);
            return false;
        }
        for (String s : split) {
            if (s.contains(" ")) {
                logger.error("Invalid target: \"{}\", namespace and name must not contain spaces", target);
                return false;
            }
        }
        return true;
    }

    private static boolean notContainsFluidStack(String s) {
        return !s.contains(FLUID_STACK);
    }

}
