package me.towdium.jecharacters.uitest.api;

public record Step(
    int index,
    StepKind kind,
    String name,
    int settleMs,
    int timeoutMs,
    StepBody body
) {

    public boolean waits() {
        return switch (kind) {
            case waitFrames, waitTicks, waitMs, waitUntil, awaitScreen, awaitClientChunk,
                    serverTicks ->
                true;
            default -> false;
        };
    }
}
