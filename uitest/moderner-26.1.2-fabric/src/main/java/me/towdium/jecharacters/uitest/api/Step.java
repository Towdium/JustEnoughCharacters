package me.towdium.jecharacters.uitest.api;

import org.jspecify.annotations.Nullable;

/**
 * One scripted step.
 *
 * @param index     position inside its scenario's step list
 * @param kind      what the step does
 * @param name      the label the builder gave it
 * @param group     the enclosing group label, or {@code null} when the step is ungrouped
 * @param settleMs  wall-clock settle after the step completes; {@code 0} means the scenario default
 * @param timeoutMs wait budget while the step keeps repeating; {@code 0} means the scenario default
 * @param body      what runs
 */
public record Step(
    int index,
    StepKind kind,
    String name,
    @Nullable String group,
    int settleMs,
    int timeoutMs,
    StepBody body
) {

    /** Whether this step is expected to repeat itself rather than finish on the first attempt. */
    public boolean waits() {
        return switch (kind) {
            case waitFrames, waitTicks, waitMs, waitUntil, waitUntilServer, awaitScreen, awaitClientChunk,
                    serverTicks ->
                true;
            default -> false;
        };
    }
}
