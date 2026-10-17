package me.towdium.jecharacters.uitest.capture;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public record CaptureRequest(
    Kind kind,
    String scenario,
    String label,
    int stepIndex,
    @Nullable Supplier<?> surface
) {

    public enum Kind {
        full,
        surface,
        sequence
    }
}
