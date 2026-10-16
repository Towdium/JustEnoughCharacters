package me.towdium.jecharacters.uitest.api;

import org.jspecify.annotations.Nullable;

public record Step(
    int index,
    StepKind kind,
    String name,
    @Nullable String group,
    int settleMs,
    int timeoutMs,
    StepBody body
) {

    public boolean waits() {
        return switch (kind) {
            case waitFrames, waitTicks, waitMs, waitUntil, waitUntilServer, awaitScreen, awaitClientChunk,
                    serverTicks ->
                true;
            default -> false;
        };
    }
}
