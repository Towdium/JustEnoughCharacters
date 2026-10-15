package me.towdium.jecharacters.uitest.api;

/**
 * One scripted step.
 *
 * <p><b>Port note.</b> The 1.21.1 record as a plain immutable class. The 1.21.1 {@code group} slot is
 * kept even though this line's Java API never sets it, because the report's step rows carry it and
 * the JSON layer (next wave) is where grouped steps first appear — dropping it would change the
 * report shape the Gradle side and jt already read.
 */
public final class Step {

    private final int index;
    private final StepKind kind;
    private final String name;
    private final String group;
    private final int settleMs;
    private final int timeoutMs;
    private final StepBody body;

    public Step(int index, StepKind kind, String name, String group, int settleMs, int timeoutMs, StepBody body) {
        this.index = index;
        this.kind = kind;
        this.name = name;
        this.group = group;
        this.settleMs = settleMs;
        this.timeoutMs = timeoutMs;
        this.body = body;
    }

    /** Position inside its scenario's step list. */
    public int index() {
        return index;
    }

    /** What the step does. */
    public StepKind kind() {
        return kind;
    }

    /** The label the builder gave it. */
    public String name() {
        return name;
    }

    /** The enclosing group label, or {@code null} when the step is ungrouped. */
    public String group() {
        return group;
    }

    /** Wall-clock settle after the step completes; {@code 0} means the scenario default. */
    public int settleMs() {
        return settleMs;
    }

    /** Wait budget while the step keeps repeating; {@code 0} means the scenario default. */
    public int timeoutMs() {
        return timeoutMs;
    }

    /** What runs. */
    public StepBody body() {
        return body;
    }

    /** Whether this step is expected to repeat itself rather than finish on the first attempt. */
    public boolean waits() {
        return kind.waits();
    }
}
