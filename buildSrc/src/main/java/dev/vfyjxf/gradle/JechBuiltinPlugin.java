package dev.vfyjxf.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class JechBuiltinPlugin implements Plugin<Project> {
    @Override
    public void apply(@NotNull Project project) {
        var outputDir = project.getLayout().getBuildDirectory().dir("jech-cache");
        var generateTargetConfig = project.getTasks().register("generateTargetConfig", GenerateTargetConfigTask.class, task -> {
            task.setGroup("Jech");
            task.setDescription("Generates the transformer target data for Jech.");
            task.getSuffixClassName().set((String) project.property("suffixClassName"));
            task.getTargetConfig().from(
                    project.getRootProject().file("generate.yaml"),
                    project.getParent().file("version_generate.yaml"),
                    project.file("platform_generate.yaml")
            );
            task.getMappingsFile().set(project.getRootProject().file("mapping.yaml"));
            task.getConfigFile().set(outputDir.map(dir -> dir.file("me/towdium/jecharacters/targets.json")));
        });
        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            var main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            // The generated file is registered as an extra output directory so that it ends up
            // in the packaged jars and stays available on the development runtime classpath,
            // while not being treated as a resource source that would leak into sourcesJar.
            main.getOutput().dir(Map.of("builtBy", generateTargetConfig), outputDir);
        });
    }


}
