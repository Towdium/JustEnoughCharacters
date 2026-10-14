package me.towdium.jecharacters.uitest.api;

/**
 * Told about every screenshot the capture pipeline writes, so the report can name the file and
 * flag a suspicious readback. Implementations call this only after a successful write.
 */
@FunctionalInterface
public interface CaptureSink {

    /**
     * @param scenario  the scenario the capture belongs to
     * @param kind      {@code FULL} or {@code ELEMENT}, so the context can adopt the real kind
     *                  instead of the one it guessed when it queued the capture
     * @param label     the caller's label for the capture
     * @param stepIndex the step that asked for it
     * @param path      the written file, relative to the output directory, element slug included
     * @param suspect   whether the image is a single flat colour
     */
    void onCapture(String scenario, String kind, String label, int stepIndex, String path, boolean suspect);
}
