package me.towdium.jecharacters.uitest.api;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public interface CapturePipeline {

    void queueFull(String scenario, String label, int stepIndex);

    default void queueSurface(String scenario, String label, int stepIndex, Supplier<?> surface) {}

    default void queueSequence(String scenario, String label, int frames, int stepIndex) {}

    void serviceOne();

    boolean isEmpty();

    default void setSink(CaptureSink sink) {}

    default @Nullable Timeline timeline(String scenario) {
        return null;
    }
}
