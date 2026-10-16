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
 * <p><b>Port note.</b> The 1.21.1 record of the same name, as a plain immutable class: Java 8 has
 * no records, so the fields are final, the accessors are the record components' names and the
 * public constructor is the canonical one. Nothing else changes.
 *
 * @param name          the unique name, from the file's own {@code name}
 * @param group         the grouping label, empty when unset
 * @param tags          the tags the {@code tag:} selection term matches
 * @param requiresWorld whether the scenario needs a loaded world
 * @param options       the per-scenario tuning, resolved against the run
 * @param steps         the declared steps, in order
 * @param source        where the file came from, for messages
 */
public final class JsonScenario {

    private final String name;
    private final String group;
    private final List<String> tags;
    private final boolean requiresWorld;
    private final ScenarioOptions options;
    private final List<JsonStep> steps;
    private final String source;

    public JsonScenario(
        String name,
        String group,
        List<String> tags,
        boolean requiresWorld,
        ScenarioOptions options,
        List<JsonStep> steps,
        String source) {
        this.name = name;
        this.group = group;
        this.tags = tags;
        this.requiresWorld = requiresWorld;
        this.options = options;
        this.steps = steps;
        this.source = source;
    }

    public String name() {
        return name;
    }

    public String group() {
        return group;
    }

    public List<String> tags() {
        return tags;
    }

    public boolean requiresWorld() {
        return requiresWorld;
    }

    public ScenarioOptions options() {
        return options;
    }

    public List<JsonStep> steps() {
        return steps;
    }

    public String source() {
        return source;
    }

    /** The step names in order, for a log line. */
    public String stepNames() {
        return steps.stream().map(JsonStep::action).reduce((a, b) -> a + " -> " + b).orElse("");
    }
}
