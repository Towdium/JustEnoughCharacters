package me.towdium.jecharacters.uitest.datadriven;

import me.towdium.jecharacters.uitest.api.ScenarioBuilder;
import net.minecraft.client.Minecraft;

import java.io.File;

public final class DataDrivenScenario {

    public static final String hotFolder = "jech-scenarios";

    public static final String resourceFolder = "uitest/scenarios";

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
        final String message = source + ": " + problem;
        builder.action("reject " + source, context -> {
            throw new ScenarioJsonException(message);
        });
    }

    public static String fallbackName(File file) {
        String base = file.getName();
        if (base.endsWith(".json")) {
            base = base.substring(0, base.length() - ".json".length());
        }
        return "json-unreadable:" + base;
    }

    public static File hotDirectory() {
        return new File(Minecraft.getMinecraft().mcDataDir, hotFolder);
    }
}
