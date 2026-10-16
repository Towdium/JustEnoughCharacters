package me.towdium.jecharacters.uitest.api;

import org.jspecify.annotations.Nullable;

/**
 * One scripted step.
 *
 * <p><b>Port note (Java 8).</b> The 1.21.1 record of the same name, as a plain immutable class: Java
 * 8 has no records, so the fields are final, the accessors are the record components' names and
 * the public constructor is the canonical one. Nothing else changes.
 *
 * @param index     position inside its scenario's step list
 * @param kind      what the step does
 * @param name      the label the builder gave it
 * @param group     the enclosing group label, or {@code null} when the step is ungrouped
 * @param settleMs  wall-clock settle after the step completes; {@code 0} means the scenario default
 * @param timeoutMs wait budget while the step keeps repeating; {@code 0} means the scenario default
 * @param body      what runs
 */
public final class Step {

    private final int index;
    private final StepKind kind;
    private final String name;
    @Nullable
    private final String group;
    private final int settleMs;
    private final int timeoutMs;
    private final StepBody body;

    public Step(
        int index,
        StepKind kind,
        String name,
        @Nullable String group,
        int settleMs,
        int timeoutMs,
        StepBody body) {
        this.index = index;
        this.kind = kind;
        this.name = name;
        this.group = group;
        this.settleMs = settleMs;
        this.timeoutMs = timeoutMs;
        this.body = body;
    }

    public int index() {
        return index;
    }

    public StepKind kind() {
        return kind;
    }

    public String name() {
        return name;
    }

    @Nullable
    public String group() {
        return group;
    }

    public int settleMs() {
        return settleMs;
    }

    public int timeoutMs() {
        return timeoutMs;
    }

    public StepBody body() {
        return body;
    }

    /** Whether this step is expected to repeat itself rather than finish on the first attempt. */
    public boolean waits() {
        switch (kind) {
            case waitFrames:
            case waitTicks:
            case waitMs:
            case waitUntil:
            case waitUntilServer:
            case awaitScreen:
            case awaitClientChunk:
            case serverTicks:
                return true;
            default:
                return false;
        }
    }
}
