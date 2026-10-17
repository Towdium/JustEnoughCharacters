package me.towdium.jecharacters.uitest.datadriven;

import com.google.gson.JsonObject;

public record JsonStep(int index, String action, JsonObject args) {

    public String describe() {
        return "step " + index + " (" + action + ")";
    }
}
