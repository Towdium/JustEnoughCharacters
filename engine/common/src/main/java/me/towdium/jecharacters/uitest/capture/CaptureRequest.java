package me.towdium.jecharacters.uitest.capture;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class CaptureRequest {

    private final Kind kind;
    private final String scenario;
    private final String label;
    private final int stepIndex;
    @Nullable
    private final Supplier<?> surface;

    public CaptureRequest(Kind kind, String scenario, String label, int stepIndex, @Nullable Supplier<?> surface) {
        this.kind = kind;
        this.scenario = scenario;
        this.label = label;
        this.stepIndex = stepIndex;
        this.surface = surface;
    }

    public Kind kind() {
        return kind;
    }

    public String scenario() {
        return scenario;
    }

    public String label() {
        return label;
    }

    public int stepIndex() {
        return stepIndex;
    }

    @Nullable
    public Supplier<?> surface() {
        return surface;
    }

    public enum Kind {
        full,
        surface,
        sequence
    }
}
