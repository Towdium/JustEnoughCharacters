package me.towdium.jecharacters.uitest.datadriven;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.towdium.jecharacters.uitest.api.ScenarioOptions;
import me.towdium.jecharacters.uitest.api.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class JsonScenarioReader {

    private static final Set<String> scenarioKeys = Set.of(
        "name", "group", "tags", "requiresWorld", "guiScale", "hideGui", "settleMs", "timeoutMs",
        "scenarioTimeoutMs", "session", "steps"
    );

    private JsonScenarioReader() {}

    public static JsonScenario parse(JsonObject root, String source) {
        Args args = new Args(source, root);
        for (String key : root.keySet()) {
            if (!scenarioKeys.contains(key)) {
                throw new ScenarioJsonException(
                    source + ": unknown scenario key '" + key + "'; allowed: "
                            + String.join(", ", scenarioKeys.stream().sorted().toList())
                );
            }
        }
        String name = args.string("name");
        if (name.isBlank() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            throw new ScenarioJsonException(source + ": name '" + name + "' must be a plain label");
        }
        String group = args.stringOr("group", "");
        List<String> tags = args.stringsOrEmpty("tags");
        boolean requiresWorld = args.boolOr("requiresWorld", true);
        ScenarioOptions options = options(args);
        List<JsonStep> steps = steps(args.array("steps"), source);
        args.done();
        return new JsonScenario(name, group, List.copyOf(tags), requiresWorld, options, List.copyOf(steps), source);
    }

    private static ScenarioOptions options(Args args) {
        ScenarioOptions options = ScenarioOptions.defaults();
        int guiScale = args.integerOr("guiScale", 0);
        if (guiScale > 0) options = options.withGuiScale(guiScale);
        options = options.withHideGui(args.boolOr("hideGui", false));
        int settleMs = args.integerOr("settleMs", 0);
        if (settleMs > 0) options = options.withSettleMs(settleMs);
        int timeoutMs = args.integerOr("timeoutMs", 0);
        if (timeoutMs > 0) options = options.withDefaultTimeoutMs(timeoutMs);
        int scenarioTimeoutMs = args.integerOr("scenarioTimeoutMs", 0);
        if (scenarioTimeoutMs > 0) options = options.withScenarioTimeoutMs(scenarioTimeoutMs);
        String session = args.stringOrNull("session");
        if (session != null) {
            options = options.withSession(session(session));
        }
        return options;
    }

    private static Session session(String raw) {
        for (Session tier : Session.values()) {
            if (tier.name().equalsIgnoreCase(raw.trim())) return tier;
        }
        List<String> known = new ArrayList<>();
        for (Session tier : Session.values()) {
            known.add(tier.name());
        }
        throw new ScenarioJsonException(
            "session '" + raw + "' is not one of " + String.join(", ", known)
        );
    }

    private static List<JsonStep> steps(JsonArray array, String source) {
        if (array.isEmpty()) {
            throw new ScenarioJsonException(source + ": 'steps' must not be empty");
        }
        List<JsonStep> steps = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            JsonElement item = array.get(index);
            if (!item.isJsonObject()) {
                throw new ScenarioJsonException(source + ": step " + index + " must be an object");
            }
            steps.add(step(item.getAsJsonObject(), index, source));
        }
        return steps;
    }

    private static JsonStep step(JsonObject declaration, int index, String source) {
        JsonElement doName = declaration.get("do");
        if (doName == null || !doName.isJsonPrimitive() || !doName.getAsJsonPrimitive().isString()
                || doName.getAsString().isBlank()) {
            throw new ScenarioJsonException(source + ": step " + index + " needs a 'do' name");
        }
        String action = doName.getAsString();
        if (!ScenarioSteps.isKnown(action)) {
            throw new ScenarioJsonException(
                source + ": step " + index + " names '" + action + "', which is not a step; known steps: "
                        + ScenarioSteps.describeVocabulary()
            );
        }
        JsonObject args = new JsonObject();
        for (String key : declaration.keySet()) {
            if (!"do".equals(key)) {
                args.add(key, declaration.get(key));
            }
        }
        return new JsonStep(index, action, args);
    }
}
