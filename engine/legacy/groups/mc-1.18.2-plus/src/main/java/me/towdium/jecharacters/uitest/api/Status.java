package me.towdium.jecharacters.uitest.api;

import java.util.Locale;

public enum Status {

    pass,

    skipped,

    fail,

    error,

    hung;

    public String wire() {
        return name().toUpperCase(Locale.ROOT);
    }

    public static Status fromWire(String raw) {
        try {
            return valueOf(raw.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown status '" + raw + "'", e);
        }
    }

    public boolean failed() {
        return this == fail || this == error || this == hung;
    }

    public static Status worst(Status a, Status b) {
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(Status status) {
        return switch (status) {
            case pass -> 0;
            case skipped -> 1;
            case fail -> 2;
            case error -> 3;
            case hung -> 4;
        };
    }
}
