package me.towdium.jecharacters.uitest.api;

public enum InputMode {

    synthetic,

    real;

    public static InputMode fromString(String raw) {
        return "real".equalsIgnoreCase(raw.trim()) ? real : synthetic;
    }
}
