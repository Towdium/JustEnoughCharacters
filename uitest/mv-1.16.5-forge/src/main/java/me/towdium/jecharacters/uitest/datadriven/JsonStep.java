package me.towdium.jecharacters.uitest.datadriven;

import com.google.gson.JsonObject;

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


    public String describe() {
        return "step " + index + " (" + action + ")";
    }
}

