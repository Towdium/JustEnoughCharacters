package me.towdium.jecharacters.uitest.datadriven;

import me.towdium.jecharacters.uitest.api.ScenarioOptions;

import java.util.List;

public final class JsonScenario {

    private final String name;
    private final String group;
    private final List<String> tags;
    private final boolean requiresWorld;
    private final ScenarioOptions options;
    private final List<JsonStep> steps;
    private final String source;

    public JsonScenario(String name, String group, List<String> tags, boolean requiresWorld, ScenarioOptions options, List<JsonStep> steps, String source) {
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


    public String stepNames() {
        return steps.stream().map(JsonStep::action).reduce((a, b) -> a + " -> " + b).orElse("");
    }
}

