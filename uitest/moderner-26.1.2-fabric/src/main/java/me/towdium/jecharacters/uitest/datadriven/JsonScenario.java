package me.towdium.jecharacters.uitest.datadriven;

import me.towdium.jecharacters.uitest.api.ScenarioOptions;

import java.util.List;

/**
 * One scenario read from JSON.
 *
 * <p>Shaped after the {@code @ClientScenario} annotation, because that is what it has to be
 * indistinguishable from once it is in the registry: a name the selection grammar matches, tags,
 * whether it needs a world, and the per-scenario tuning. What it adds is its step list, which is
 * interpreted rather than compiled.
 *
 * @param name          the unique name, from the file's own {@code name}
 * @param group         the grouping label, empty when unset
 * @param tags          the tags the {@code tag:} selection term matches
 * @param requiresWorld whether the scenario needs a loaded world
 * @param options       the per-scenario tuning, resolved against the run
 * @param steps         the declared steps, in order
 * @param source        where the file came from, for messages
 */
public record JsonScenario(
    String name,
    String group,
    List<String> tags,
    boolean requiresWorld,
    ScenarioOptions options,
    List<JsonStep> steps,
    String source
) {

    /** The step names in order, for a log line. */
    public String stepNames() {
        return steps.stream().map(JsonStep::action).reduce((a, b) -> a + " -> " + b).orElse("");
    }
}
