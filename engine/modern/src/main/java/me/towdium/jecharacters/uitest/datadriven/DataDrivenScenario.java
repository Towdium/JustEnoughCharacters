package me.towdium.jecharacters.uitest.datadriven;

import me.towdium.jecharacters.uitest.api.ScenarioBuilder;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

public final class DataDrivenScenario {

    private DataDrivenScenario() {}

    public static void define(JsonScenario scenario, ScenarioBuilder builder) {
        builder.log("json scenario '" + scenario.name() + "' from " + scenario.source()
                + ": " + scenario.steps().size() + " step(s)");
        for (JsonStep step : scenario.steps()) {
            ScenarioSteps.apply(builder, step);
        }
    }

    public static void defineFailure(String name, String source, String problem, ScenarioBuilder builder) {
        builder.log("json scenario '" + name + "' from " + source + " could not be read");
        builder.action("reject " + source, context -> {
            throw new ScenarioJsonException(source + ": " + problem);
        });
    }

    public static String fallbackName(Path file) {
        String base = file.getFileName().toString();
        if (base.endsWith(".json")) {
            base = base.substring(0, base.length() - ".json".length());
        }
        return "json-unreadable:" + base;
    }

    public static Path hotDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(hotFolder);
    }

    public static final String hotFolder = "jech-scenarios";

    public static final String resourceFolder = "uitest/scenarios";
}
