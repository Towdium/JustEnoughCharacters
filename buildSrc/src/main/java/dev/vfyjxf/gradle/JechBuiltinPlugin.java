package dev.vfyjxf.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Jar;
import org.jetbrains.annotations.NotNull;

public class JechBuiltinPlugin implements Plugin<Project> {
    @Override
    public void apply(@NotNull Project project) {
        var generateTargetConfig = project.getTasks().register("generateTargetConfig", GenerateTargetConfigTask.class, task -> {
            task.setGroup("Jech");
            task.setDescription("Generates the transformer target data for Jech.");
            task.getSuffixClassName().set((String) project.property("suffixClassName"));
            task.getTargetConfig().from(project.getRootProject().file("generate.yaml"));
            task.getMappingsFile().set(project.getRootProject().file("mapping.yaml"));
            var outputDir = project.getLayout().getBuildDirectory().file("jech-cache/targets.json");
            task.getConfigFile().set(outputDir);
        });
        project.getTasks().named("jar", Jar.class, jar -> {
            jar.from(generateTargetConfig, copySpec -> {
                copySpec.into("me/towdium/jecharacters");
            });
        });
    }


}
