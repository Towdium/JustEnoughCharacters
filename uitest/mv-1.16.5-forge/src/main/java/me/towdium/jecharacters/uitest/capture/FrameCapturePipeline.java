package me.towdium.jecharacters.uitest.capture;

import net.minecraft.client.renderer.texture.NativeImage;
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
import java.nio.file.Paths;

public final class FrameCapturePipeline implements CapturePipeline {

    public static final String screenshotsDirectory = "screenshots";

    private static final Logger logger = LoggerFactory.getLogger("jechtest/uitest");

    private final Path outDir;
    private final Deque<CaptureRequest> pending = new ArrayDeque<>();
    private @Nullable CaptureSink sink;

    public FrameCapturePipeline() {
        this(Paths.get("jech-uitest"));
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

    static String kindOf(CaptureRequest.Kind kind) {
        switch (kind) {
            case surface:
                return "SURFACE";
            case sequence:
                return "SEQUENCE";
            case full:
                return "FULL";
        }
        throw new IllegalStateException("unreachable capture kind " + kind);
    }

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
