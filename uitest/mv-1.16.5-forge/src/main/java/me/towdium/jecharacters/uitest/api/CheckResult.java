package me.towdium.jecharacters.uitest.api;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class CheckResult {

    private final String label;

    private final boolean passed;

    private final @Nullable String detail;

    public CheckResult(String label, boolean passed, @Nullable String detail) {
        this.label = label;
        this.passed = passed;
        this.detail = detail;
    }

    public static CheckResult of(String label, boolean passed) {
        return new CheckResult(label, passed, null);
    }

    public String label() {
        return label;
    }

    public boolean passed() {
        return passed;
    }

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
