package me.towdium.jecharacters.uitest.datadriven;

import me.towdium.jecharacters.uitest.api.ScenarioBuilder;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/**
 * The interpreter: turns a {@link JsonScenario}'s steps into the steps of a
 * {@link ScenarioBuilder}.
 *
 * <p>A scenario that was rejected while it was read still gets here — as a single step that fails and
 * says why. That is deliberate, and it is the same fail-closed rule the rest of the harness follows:
 * a JSON file nobody can read is a scenario that appears in the report as a failure naming its file
 * and its problem, never a scenario that quietly is not there. Dropping it silently would make a
 * mistyped step a test that passes because it never ran.
 */
public final class DataDrivenScenario {

    private DataDrivenScenario() {}

    /** Applies every step of a parsed scenario, in order. */
    public static void define(JsonScenario scenario, ScenarioBuilder builder) {
        builder.log("json scenario '" + scenario.name() + "' from " + scenario.source()
                + ": " + scenario.steps().size() + " step(s)");
        for (JsonStep step : scenario.steps()) {
            ScenarioSteps.apply(builder, step);
        }
    }

    /**
     * The step list of a scenario that could not be read: one step that reports the problem.
     *
     * <p>It is an {@code action} rather than a {@code check} because the failure has to be the
     * scenario's own, so the report can carry it as an error with the message attached instead of as
     * a check whose label has to be read to find out what went wrong.
     */
    public static void defineFailure(String name, String source, String problem, ScenarioBuilder builder) {
        builder.log("json scenario '" + name + "' from " + source + " could not be read");
        builder.action("reject " + source, context -> {
            throw new ScenarioJsonException(source + ": " + problem);
        });
    }

    /**
     * Whether a scenario name is one this layer invented for a file that could not be read.
     *
     * <p>A broken file still needs a unique registry name, and the name it would have had is exactly
     * what is not known yet, so it is derived from the file.
     */
    public static String fallbackName(Path file) {
        String base = file.getFileName().toString();
        if (base.endsWith(".json")) {
            base = base.substring(0, base.length() - ".json".length());
        }
        return "json-unreadable:" + base;
    }

    /** The game directory a run's hot scenario folder is looked for under. */
    public static Path hotDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(hotFolder);
    }

    /** The folder name, relative to the game directory, that scenarios are picked up from hot. */
    public static final String hotFolder = "jech-scenarios";

    /** The resource folder, inside any loaded mod, that scenarios are picked up from. */
    public static final String resourceFolder = "uitest/scenarios";
}
