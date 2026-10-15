package me.towdium.jecharacters.uitest.datadriven;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.towdium.jecharacters.uitest.api.ScenarioOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads one scenario file into a {@link JsonScenario}, refusing anything it does not fully
 * understand.
 *
 * <p>Validation is total rather than incremental: the scenario block and every step block are checked
 * before anything is returned, so a first run of a new file reports what is wrong with it rather than
 * one problem per attempt. Unknown keys are refused at both levels — at the top because a misspelled
 * {@code requiresWorld} would otherwise be a scenario that ran without the world it needs, and inside
 * a step because {@link Args} owns that check.
 *
 * <p><b>Port note.</b> The 1.21.1 class of the same name with one key removed: {@code session} is not
 * in the allowed set, because this checkout's {@link ScenarioOptions} has no session tier (the run
 * always uses the one world it created). A file that writes {@code session} is refused by name, which
 * is the fail-closed form of "this line cannot honour it yet".
 */
public final class JsonScenarioReader {

    private static final Set<String> scenarioKeys = new LinkedHashSet<String>(Arrays.asList(
            "name", "group", "tags", "requiresWorld", "guiScale", "hideGui", "settleMs", "timeoutMs",
            "scenarioTimeoutMs", "steps"
    ));

    private JsonScenarioReader() {}

    /** Parses a scenario object; {@code source} names the file in every message. */
    public static JsonScenario parse(JsonObject root, String source) {
        Args args = new Args(source, root);
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!scenarioKeys.contains(entry.getKey())) {
                throw new ScenarioJsonException(
                        source + ": unknown scenario key '" + entry.getKey() + "'; allowed: "
                                + String.join(", ", sorted(scenarioKeys))
                );
            }
        }
        String name = args.string("name");
        if (name.trim().isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            throw new ScenarioJsonException(source + ": name '" + name + "' must be a plain label");
        }
        String group = args.stringOr("group", "");
        List<String> tags = args.stringsOrEmpty("tags");
        boolean requiresWorld = args.boolOr("requiresWorld", true);
        ScenarioOptions options = options(args);
        List<JsonStep> steps = steps(args.array("steps"), source);
        args.done();
        return new JsonScenario(
                name, group, tags, requiresWorld, options, steps, source);
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
        return options;
    }

    private static List<JsonStep> steps(JsonArray array, String source) {
        if (array.size() == 0) {
            throw new ScenarioJsonException(source + ": 'steps' must not be empty");
        }
        List<JsonStep> steps = new ArrayList<JsonStep>(array.size());
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
                || doName.getAsString().trim().isEmpty()) {
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
        for (Map.Entry<String, JsonElement> entry : declaration.entrySet()) {
            if (!"do".equals(entry.getKey())) {
                args.add(entry.getKey(), entry.getValue());
            }
        }
        return new JsonStep(index, action, args);
    }

    private static List<String> sorted(Set<String> values) {
        List<String> sorted = new ArrayList<String>(values);
        sorted.sort(String::compareTo);
        return sorted;
    }
}
