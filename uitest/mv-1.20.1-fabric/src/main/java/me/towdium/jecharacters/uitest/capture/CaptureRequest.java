package me.towdium.jecharacters.uitest.capture;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * One queued screenshot request.
 *
 * <p><b>Port note.</b> The upstream harness also had a per-widget {@code element} kind; it is not
 * here, because a crop needs the CloudLib widget tree to resolve its rectangle.
 *
 * @param kind      what is wanted
 * @param scenario  the owning scenario, used as the directory name
 * @param label     a short suffix describing why the capture was taken; for {@link Kind#sequence} it
 *                  already carries the frame suffix, which is why such a request has no step prefix
 * @param stepIndex the step that queued it
 * @param surface   the surface supplier for {@link Kind#surface}; evaluated at service time, on the
 *                  render thread, so it names this frame's target rather than the frame the request
 *                  was queued in. {@code null} otherwise
 */
public record CaptureRequest(
    Kind kind,
    String scenario,
    String label,
    int stepIndex,
    @Nullable Supplier<?> surface
) {

    /** What the request wants captured. */
    public enum Kind {
        /** The whole main render target. */
        full,
        /** An arbitrary colour surface, resolved through its supplier. */
        surface,
        /** One frame of a labelled sequence, written as {@code <label>_%04d.png}. */
        sequence
    }
}
