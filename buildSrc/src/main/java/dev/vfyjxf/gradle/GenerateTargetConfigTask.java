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
    private static final String FLUID_STACK = "Lnet/neoforged/neoforge/fluids/FluidStack";

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
            Map<String, String> mappings = mappingFile.exists() && mappingFile.isFile() ?
                    yaml.load(Files.readString(mappingFile.toPath())) : new HashMap<>();
            var project = getProject();
            var buildPath = project.getLayout().getBuildDirectory().getAsFile().get().toPath();
            Path targetsJsonPath = getConfigFile().getAsFile().get().toPath();
            boolean fabric = project.getName().contains("fabric");

            //region read all generator config files
            for (File file : configFiles) {
                if (file.isDirectory()) continue;
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
                            .map(mapped -> {
                                for (var mappingEntry : mappings.entrySet()) {
                                    if (mapped.contains(mappingEntry.getKey())) {
                                        mapped = mapped.replace(mappingEntry.getKey(), mappingEntry.getValue());
                                    }
                                }
                                return mapped;
                            })
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
            String jsonStr = new GsonBuilder().setPrettyPrinting().create().toJson(targetsJson);
            Files.writeString(targetsJsonPath,
                    jsonStr,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex == -1 ? "" : fileName.substring(dotIndex + 1);
    }

    private boolean checkTargetFormat(String target) {
        Logger logger = getProject().getLogger();
        String[] split = target.split(":");
        if (split.length != 2) {
            logger.error("Invalid target: \"${}\", expected format is namespace:name", target);
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
