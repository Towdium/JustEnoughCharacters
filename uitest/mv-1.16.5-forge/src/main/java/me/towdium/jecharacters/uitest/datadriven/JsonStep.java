package me.towdium.jecharacters.uitest.datadriven;

import com.google.gson.JsonObject;

/**
 * One declared step: the step name under {@code do}, and its parameters.
 *
 * @param index  position in the scenario's step list, for messages and the report
 * @param action the step name ({@code press_bind}, {@code type}, …)
 * @param args   the step's parameters, validated when the step is applied
 */
public final class JsonStep {

    private final int index;
    private final String action;
    private final JsonObject args;

    public JsonStep(int index, String action, JsonObject args) {
        this.index = index;
        this.action = action;
        this.args = args;
    }

    public int index() {
        return index;
    }

    public String action() {
        return action;
    }

    public JsonObject args() {
        return args;
    }


    /** How this step is named in an error message. */
    public String describe() {
        return "step " + index + " (" + action + ")";
    }
}

