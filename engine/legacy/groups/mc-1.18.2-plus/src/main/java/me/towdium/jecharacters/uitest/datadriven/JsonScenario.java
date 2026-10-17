package me.towdium.jecharacters.uitest.datadriven;

import me.towdium.jecharacters.uitest.api.ScenarioOptions;

import java.util.List;

public record JsonScenario(
    String name,
    String group,
    List<String> tags,
    boolean requiresWorld,
    ScenarioOptions options,
    List<JsonStep> steps,
    String source
) {

    public String stepNames() {
        return steps.stream().map(JsonStep::action).reduce((a, b) -> a + " -> " + b).orElse("");
    }
}
