package me.towdium.jecharacters.uitest.api;

import java.util.Locale;

/**
 * Outcome of a step, a scenario or a whole run.
 *
 * <p>Constants are lowerCamelCase because this repository's style gate rejects UPPER_SNAKE enum
 * constants; {@link #wire()} carries the upper-case spelling the report files and the Gradle
 * verification task read.
 *
 * <p>The ranking behind {@link #worst(Status, Status)} is explicit rather than ordinal: a roll-up
 * must keep the outcome that matters most, with {@link #hung} beating everything below it.
 */
public enum Status {

    /** The step, scenario or run completed and every check passed. */
    pass,

    /** Not reached: the run was aborted earlier or a needed capability is absent. */
    skipped,

    /** Reached, but a check failed. */
    fail,

    /** Reached, but threw. */
    error,

    /** The run stopped making progress and a watchdog reported it. */
    hung;

    /** The upper-case spelling used in the report files. */
    public String wire() {
        return name().toUpperCase(Locale.ROOT);
    }

    /** Reads either spelling back; anything unrecognised means {@link #error}. */
    public static Status fromWire(String raw) {
        try {
            return valueOf(raw.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown status '" + raw + "'", e);
        }
    }

    /** Whether this outcome should fail a run. */
    public boolean failed() {
        return this == fail || this == error || this == hung;
    }

    /** The outcome that matters more of the two. */
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
