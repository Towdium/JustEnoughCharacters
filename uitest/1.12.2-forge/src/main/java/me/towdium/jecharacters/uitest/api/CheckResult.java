package me.towdium.jecharacters.uitest.api;

public final class CheckResult {

    private final String label;
    private final boolean passed;
    private final String detail;

    public CheckResult(String label, boolean passed, String detail) {
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

    public String detail() {
        return detail;
    }
}
