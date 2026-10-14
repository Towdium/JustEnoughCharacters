package me.towdium.jecharacters.uitest.capture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The readback of an arbitrary colour surface — any render target; the main framebuffer is not
 * the only thing a run may want on disk.
 *
 * <p>It reads the render target's <em>colour texture</em>, never the swap chain, so whether the
 * window is focused, occluded or even shown does not matter — which is what makes background and
 * headless runs capture correctly.
 *
 * <p><b>Port note (MC 1.21.10).</b> The 1.21.1 body bound the colour texture id and called
 * {@code NativeImage.downloadTexture}/{@code flipY} directly; all three are gone with the GL API
 * they belonged to. The readback itself is now vanilla's own
 * {@link Screenshot#takeScreenshot(RenderTarget, Consumer)} — the body behind F2 — which copies the
 * colour texture into a mapped buffer, builds the {@code NativeImage} and flips it top-down. This
 * class is that call plus the two guards the harness needs, so no third readback path exists.
 *
 * <p>The delivery is <em>asynchronous</em>, and that is the other 1.21.10 change: the copy is queued
 * as a fenced GPU task and the image reaches the consumer on a later frame (see
 * {@link FrameCapturePipeline}, which keeps the run's frame pump parked until it does).
 *
 * <p><b>Port note.</b> The upstream harness also described a CloudLib in-world panel's own
 * offscreen surface; that branch is not here, because it needed the CloudLib render API. Anything
 * that is not a Minecraft {@link RenderTarget} is refused rather than guessed at.
 */
public final class SurfaceCapture {

    private SurfaceCapture() {}

    /**
     * Requests a download of whatever {@code surface} names.
     *
     * @param surface a {@link RenderTarget}, or {@code null}
     * @param consumer given the downloaded top-down image on a later frame
     * @return false when the shape is unknown or the surface has nothing to read, in which case the
     *         consumer is never called
     */
    public static boolean request(@Nullable Object surface, Consumer<NativeImage> consumer) {
        if (surface instanceof RenderTarget target) {
            return request(target, consumer);
        }
        return false;
    }

    /**
     * Requests a download of {@code target}'s colour texture at the target's pixel size.
     *
     * @return false when the target has no size or no colour texture yet — the state
     *         {@link Screenshot#takeScreenshot} refuses outright rather than returning nothing
     */
    public static boolean request(RenderTarget target, Consumer<NativeImage> consumer) {
        if (target.width <= 0 || target.height <= 0 || target.getColorTexture() == null) {
            return false;
        }
        Screenshot.takeScreenshot(target, consumer);
        return true;
    }
}
