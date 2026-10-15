package me.towdium.jecharacters.uitest.api;

/**
 * One assertion outcome recorded by a scenario step.
 *
 * <p><b>Port note.</b> The 1.21.1 record of the same name as a plain immutable class: Java 8 has no
 * records, so the fields are final, the accessors are the record components' names, and the public
 * constructor is the canonical one. Nothing else changes.
 */
public final class CheckResult {

    private final String label;
    private final boolean passed;
    private final String detail;

    public CheckResult(String label, boolean passed, String detail) {
        this.label = label;
        this.passed = passed;
        this.detail = detail;
    }

    /** A result without a detail string. */
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
