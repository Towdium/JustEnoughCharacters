package me.towdium.jecharacters.uitest.api;

import org.jspecify.annotations.Nullable;

/**
 * One assertion outcome recorded by a scenario step.
 *
 * <p><b>Port note (Java 8).</b> The 1.21.1 record of the same name, as a plain immutable class: Java
 * 8 has no records, so the fields are final, the accessors are the record components' names and
 * the public constructor is the canonical one. Nothing else changes.
 *
 * @param label  what was asserted, in the scenario's own words
 * @param passed whether it held
 * @param detail the observed value, or {@code null} when the label says enough
 */
public final class CheckResult {

    private final String label;
    private final boolean passed;
    @Nullable
    private final String detail;

    public CheckResult(String label, boolean passed, @Nullable String detail) {
        this.label = label;
        this.passed = passed;
        this.detail = detail;
    }

    public String label() {
        return label;
    }

    public boolean passed() {
        return passed;
    }

    @Nullable
    public String detail() {
        return detail;
    }

    /** A result without a detail string. */
    public static CheckResult of(String label, boolean passed) {
        return new CheckResult(label, passed, null);
    }
}
