package me.towdium.jecharacters.uitest.api;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * One assertion outcome recorded by a scenario step.
 *
 * <p><b>Port note.</b> The 1.21.1 record as a plain immutable class: Java 8 has no records, so the
 * three components are private final fields, the constructor is the canonical one, and the accessors
 * keep the component names, with {@code detail} as {@code @Nullable} where the component was.
 * {@code equals}, {@code hashCode} and {@code toString} are spelled out component for component,
 * because a result is a value. The record's {@code @param} javadoc moved onto the accessors.
 */
public final class CheckResult {

    private final String label;

    private final boolean passed;

    private final @Nullable String detail;

    public CheckResult(String label, boolean passed, @Nullable String detail) {
        this.label = label;
        this.passed = passed;
        this.detail = detail;
    }

    /** A result without a detail string. */
    public static CheckResult of(String label, boolean passed) {
        return new CheckResult(label, passed, null);
    }

    /** What was asserted, in the scenario's own words. */
    public String label() {
        return label;
    }

    /** Whether it held. */
    public boolean passed() {
        return passed;
    }

    /** The observed value, or {@code null} when the label says enough. */
    public @Nullable String detail() {
        return detail;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CheckResult)) return false;
        CheckResult that = (CheckResult) other;
        return passed == that.passed
            && Objects.equals(label, that.label)
            && Objects.equals(detail, that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, passed, detail);
    }

    @Override
    public String toString() {
        return "CheckResult[label=" + label + ", passed=" + passed + ", detail=" + detail + "]";
    }
}
