package me.towdium.jecharacters.uitest.api;

import java.util.Locale;

public enum InputMode {

    synthetic,

    real;

    public static InputMode fromString(String value) {
        if (value == null) return synthetic;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "":
            case "synthetic":
                return synthetic;
            case "real":
                return real;
            default:
                throw new IllegalArgumentException("unknown input mode '" + value + "' (expected SYNTHETIC or REAL)");
        }
    }
}
