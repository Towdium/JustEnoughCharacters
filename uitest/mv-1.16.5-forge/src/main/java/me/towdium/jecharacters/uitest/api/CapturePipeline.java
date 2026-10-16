package me.towdium.jecharacters.uitest.api;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Queues screenshots for the frame pump to service, one per frame, so every image shows the state
 * the step that asked for it produced.
 *
 * <p>Readback is of the render target's colour texture rather than the swap chain, which is why a
 * window that is unfocused, occluded or hidden still captures. Servicing must never throw: a broken
 * capture may not break the run.
 *
 * <p><b>Port note.</b> The crop-to-widget request of the upstream CloudLib harness is not here: a
 * crop resolves its rectangle from the CloudLib widget tree, which this harness deliberately does
 * not carry. The full-frame, colour-surface and frame-sequence captures are what remain, and they
 * cover everything a world/screen-input scenario needs to leave evidence behind.
 */
public interface CapturePipeline {

    /** Queue a full-frame screenshot. */
    void queueFull(String scenario, String label, int stepIndex);

    /**
     * Queue a readback of an arbitrary colour surface — a Minecraft render target — resolved through
     * {@code surface} on the frame it is serviced.
     *
     * @param surface a supplier of the surface to read; resolved at service time so it names the
     *                current frame's target, and skipped, with a log line, when it yields nothing
     *                this implementation can read
     */
    default void queueSurface(String scenario, String label, int stepIndex, Supplier<?> surface) {}

    /**
     * Queue a labelled run of {@code frames} captures, one per frame, each written as
     * {@code <label>_%04d.png}. The queue is drained one request per frame, so the images are
     * consecutive frames in order.
     */
    default void queueSequence(String scenario, String label, int frames, int stepIndex) {}

    /** Service at most one queued request. Called once per frame by the engine. */
    void serviceOne();

    /** Whether anything is still queued. */
    boolean isEmpty();

    /** Optional: receive a callback per written file, for the report's capture list. */
    default void setSink(CaptureSink sink) {}

    /** Optional: a fresh timeline log for {@code scenario}, or {@code null} when none is written. */
    default @Nullable Timeline timeline(String scenario) {
        return null;
    }
}
