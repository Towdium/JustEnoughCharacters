package me.towdium.jecharacters.uitest.api;

import java.util.Objects;

public final class Step {

    private final int index;

    private final StepKind kind;

    private final String name;

    private final int settleMs;

    private final int timeoutMs;

    private final StepBody body;

    public Step(
        int index,
        StepKind kind,
        String name,
        int settleMs,
        int timeoutMs,
        StepBody body
    ) {
        this.index = index;
        this.kind = kind;
        this.name = name;
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

    public int settleMs() {
        return settleMs;
    }

    public int timeoutMs() {
        return timeoutMs;
    }

    public StepBody body() {
        return body;
    }

    public boolean waits() {
        switch (kind) {
            case waitFrames:
            case waitTicks:
            case waitMs:
            case waitUntil:
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
            && Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, kind, name, settleMs, timeoutMs, body);
    }

    @Override
    public String toString() {
        return "Step[index=" + index
            + ", kind=" + kind
            + ", name=" + name
            + ", settleMs=" + settleMs
            + ", timeoutMs=" + timeoutMs
            + ", body=" + body
            + "]";
    }
}
