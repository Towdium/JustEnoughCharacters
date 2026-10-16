package me.towdium.jecharacters.uitest.capture;

import com.mojang.blaze3d.platform.NativeImage;
import me.towdium.jecharacters.uitest.api.CapturePipeline;
import me.towdium.jecharacters.uitest.api.CaptureSink;
import me.towdium.jecharacters.uitest.api.Timeline;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * The frame-service implementation of {@link CapturePipeline}: a queue drained one request per
 * frame, writing PNGs under {@code <outDir>/screenshots/<scenario>/}.
 *
 * <p>The readback is of the render target's colour texture, so whether the window is focused,
 * occluded or hidden never matters. Nothing here throws out into a run — a capture that cannot be
 * taken or written is logged and dropped — and every image is held in a try-with-resources
 * statement, so a failed run cannot leak off-heap memory either.
 *
 * <p>The queue carries full frames, arbitrary colour surfaces ({@link SurfaceCapture}) and labelled
 * frame sequences ({@link FrameSequenceCapture}), and each scenario gets a {@link LayoutTimeline}
 * written beside the report.
 *
 * <p><b>Port note.</b> The upstream pipeline's element crop, its tree-snapshot store and its
 * golden-image comparator are gone: they all need the CloudLib widget tree or the golden baselines
 * this batch does not port.
 */
public final class FrameCapturePipeline implements CapturePipeline {

    /** Directory under the run output root that holds every screenshot, one subdirectory per scenario. */
    public static final String screenshotsDirectory = "screenshots";

    private static final Logger logger = LoggerFactory.getLogger("jechtest/uitest");

    private final Path outDir;
    private final Deque<CaptureRequest> pending = new ArrayDeque<>();
    private @Nullable CaptureSink sink;

    /** Uses the default run output directory, {@code jech-uitest}. */
    public FrameCapturePipeline() {
        this(Path.of("jech-uitest"));
    }

    public FrameCapturePipeline(Path outDir) {
        this.outDir = outDir;
    }

    public Path outDir() {
        return outDir;
    }

    @Override
    public void queueFull(String scenario, String label, int stepIndex) {
        pending.addLast(new CaptureRequest(CaptureRequest.Kind.full, scenario, label, stepIndex, null));
    }

    @Override
    public void queueSurface(String scenario, String label, int stepIndex, Supplier<?> surface) {
        pending.addLast(new CaptureRequest(CaptureRequest.Kind.surface, scenario, label, stepIndex, surface));
    }

    @Override
    public void queueSequence(String scenario, String label, int frames, int stepIndex) {
        FrameSequenceCapture.enqueue(pending, scenario, label, frames, stepIndex);
    }

    @Override
    public void serviceOne() {
        CaptureRequest request = pending.pollFirst();
        if (request == null) {
            return;
        }
        service(request);
    }

    @Override
    public boolean isEmpty() {
        return pending.isEmpty();
    }

    @Override
    public void setSink(CaptureSink newSink) {
        this.sink = newSink;
    }

    @Override
    public Timeline timeline(String scenario) {
        return new LayoutTimeline(outDir, scenario);
    }

    /** How many requests are still queued. */
    public int pendingCount() {
        return pending.size();
    }

    private void service(CaptureRequest request) {
        if (request.kind() == CaptureRequest.Kind.surface) {
            serviceSurface(request);
            return;
        }
        try (FrameCapture frame = FrameCapture.grab()) {
            if (frame == null) {
                logger.warn("[uitest] capture {} / {} skipped: no render target", request.scenario(), request.label());
                return;
            }
            write(request, frame.image());
        } catch (IOException | RuntimeException e) {
            logger.warn("[uitest] capture {} / {} failed: {}", request.scenario(), request.label(), e.toString());
        }
    }

    /**
     * Reads the request's surface and writes it; a surface that cannot be resolved, or that has
     * nothing on it yet, is logged and dropped like every other skipped capture.
     */
    private void serviceSurface(CaptureRequest request) {
        Supplier<?> supplier = request.surface();
        Object surface = supplier == null ? null : supplier.get();
        NativeImage image = null;
        try {
            image = SurfaceCapture.grab(surface);
            if (image == null) {
                logger.warn(
                    "[uitest] surface capture {} / {} skipped: {} has no readable surface",
                    request.scenario(),
                    request.label(),
                    surface == null ? "the supplier yielded nothing" : surface.getClass().getName()
                );
                return;
            }
            write(request, image);
        } catch (IOException | RuntimeException e) {
            logger.warn("[uitest] surface capture {} / {} failed: {}", request.scenario(), request.label(), e);
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private void write(CaptureRequest request, NativeImage image) throws IOException {
        String relative = relativePath(request);
        boolean suspect = FrameCapture.write(image, outDir.resolve(relative));
        if (suspect) {
            logger.warn("[uitest] capture {} is a single flat colour - wrong framebuffer?", relative);
        }
        CaptureSink currentSink = sink;
        if (currentSink != null) {
            currentSink.onCapture(
                request.scenario(),
                kindOf(request.kind()),
                request.label(),
                request.stepIndex(),
                relative,
                suspect
            );
        }
    }

    /** The report spelling of a capture kind, matching {@link me.towdium.jecharacters.uitest.api.CaptureRef}. */
    static String kindOf(CaptureRequest.Kind kind) {
        return switch (kind) {
            case surface -> "SURFACE";
            case sequence -> "SEQUENCE";
            case full -> "FULL";
        };
    }

    /**
     * {@code <scenario>/<step index zero padded>_<label>.png}.
     *
     * <p>A sequence frame carries no step prefix: its label already ends in the frame's index, and the
     * whole run has to be listable as one glob.
     */
    private static String relativePath(CaptureRequest request) {
        String directory = screenshotsDirectory + "/" + sanitize(request.scenario()) + "/";
        if (request.kind() == CaptureRequest.Kind.sequence) {
            return directory + sanitize(request.label()) + ".png";
        }
        return directory + String.format("%02d_%s", request.stepIndex(), sanitize(request.label())) + ".png";
    }

    static String sanitize(String raw) {
        return raw.replaceAll("[^A-Za-z0-9._-]+", "_");
    }
}
