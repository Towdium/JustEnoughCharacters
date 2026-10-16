package me.towdium.jecharacters.uitest.api;

import org.jspecify.annotations.Nullable;

/**
 * One assertion outcome recorded by a scenario step.
 *
 * @param label  what was asserted, in the scenario's own words
 * @param passed whether it held
 * @param detail the observed value, or {@code null} when the label says enough
 */
public record CheckResult(String label, boolean passed, @Nullable String detail) {

    /** A result without a detail string. */
    public static CheckResult of(String label, boolean passed) {
        return new CheckResult(label, passed, null);
    }
}
