package me.towdium.jecharacters.uitest.api;

public interface CapturePipeline {

    void queueFull(String scenario, String label, int stepIndex);

    void serviceOne();

    boolean isEmpty();

    default void setSink(CaptureSink sink) {}
}
