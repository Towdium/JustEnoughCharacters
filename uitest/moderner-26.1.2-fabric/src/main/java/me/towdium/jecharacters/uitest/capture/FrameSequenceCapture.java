package me.towdium.jecharacters.uitest.capture;

import java.util.Deque;
import java.util.Locale;

/**
 * A labelled run of consecutive frames: {@link #enqueue} turns one request for {@code frames} frames
 * into that many queued captures, and each is written as {@code <label>_%04d.png}.
 *
 * <p>The queue is the existing one-request-per-frame service, so the run really gets one image per
 * frame in order — frame {@code n} is serviced on the frame after the one that queued it, which is
 * exactly the state the step that asked for it produced. Nothing is buffered in memory: each frame is
 * downloaded, written and released before the next is serviced.
 *
 * <p>The step index is deliberately not part of the name: a sequence outlives the step that started
 * it, and a reader that has the label can list the whole run as one glob.
 */
public final class FrameSequenceCapture {

    private FrameSequenceCapture() {}

    /** Queues {@code frames} captures, one per frame, under {@code <label>_0000 … <label>_%04d}. */
    public static void enqueue(
        Deque<CaptureRequest> pending,
        String scenario,
        String label,
        int frames,
        int stepIndex
    ) {
        for (int frame = 0; frame < frames; frame++) {
            pending.addLast(
                new CaptureRequest(
                    CaptureRequest.Kind.sequence,
                    scenario,
                    frameLabel(label, frame),
                    stepIndex,
                    null
                )
            );
        }
    }

    /** The label one frame of a sequence carries: {@code <label>_%04d}. */
    public static String frameLabel(String label, int frame) {
        return FrameCapturePipeline.sanitize(label) + "_" + String.format(Locale.ROOT, "%04d", frame);
    }

    /**
     * Where one frame of a sequence lands, relative to the run's output directory:
     * {@code screenshots/<scenario>/<label>_%04d.png}.
     */
    public static String relativePath(String scenario, String label) {
        return FrameCapturePipeline.screenshotsDirectory + "/" + FrameCapturePipeline.sanitize(scenario) + "/" + label
                + ".png";
    }
}
