package me.towdium.jecharacters.uitest.api;

import org.jspecify.annotations.Nullable;

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

    public static CheckResult of(String label, boolean passed) {
        return new CheckResult(label, passed, null);
    }
}
