package me.towdium.jecharacters.uitest.api;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * One scripted step.
 *
 * <p><b>Port note.</b> The 1.21.1 record as a plain immutable class: Java 8 has no records, so the
 * seven components are private final fields, the constructor is the canonical one, and the accessors
 * keep the component names, with {@code group} as {@code @Nullable} where the component was.
 * {@code equals}, {@code hashCode} and {@code toString} are spelled out component for component,
 * because a step is a value. The record's {@code @param} javadoc moved onto the accessors, and
 * {@link #waits()} is a switch statement whose labels are stacked rather than a multi-label switch
 * expression — the arms are the same ones, in the same order, with the same {@code false} default.
 */
public final class Step {

    private final int index;

    private final StepKind kind;

    private final String name;

    private final @Nullable String group;

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
        StepBody body
    ) {
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
    public @Nullable String group() {
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

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Step)) return false;
        Step that = (Step) other;
        return index == that.index
            && settleMs == that.settleMs
            && timeoutMs == that.timeoutMs
            && kind == that.kind
            && Objects.equals(name, that.name)
            && Objects.equals(group, that.group)
            && Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, kind, name, group, settleMs, timeoutMs, body);
    }

    @Override
    public String toString() {
        return "Step[index=" + index
            + ", kind=" + kind
            + ", name=" + name
            + ", group=" + group
            + ", settleMs=" + settleMs
            + ", timeoutMs=" + timeoutMs
            + ", body=" + body
            + "]";
    }
}
