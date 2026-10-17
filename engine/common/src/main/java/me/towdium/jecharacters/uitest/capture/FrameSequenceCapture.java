package me.towdium.jecharacters.uitest.capture;

import java.util.Deque;
import java.util.Locale;

public final class FrameSequenceCapture {

    private FrameSequenceCapture() {}

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

    public static String frameLabel(String label, int frame) {
        return FrameCapturePipeline.sanitize(label) + "_" + String.format(Locale.ROOT, "%04d", frame);
    }

    public static String relativePath(String scenario, String label) {
        return FrameCapturePipeline.screenshotsDirectory + "/" + FrameCapturePipeline.sanitize(scenario) + "/" + label
                + ".png";
    }
}
