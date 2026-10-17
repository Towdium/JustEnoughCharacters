package me.towdium.jecharacters.uitest.api;

@FunctionalInterface
public interface CaptureSink {

    void onCapture(String scenario, String kind, String label, int stepIndex, String path, boolean suspect);
}
