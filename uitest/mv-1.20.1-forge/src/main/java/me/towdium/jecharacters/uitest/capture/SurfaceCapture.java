package me.towdium.jecharacters.uitest.capture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jspecify.annotations.Nullable;

/**
 * The readback of an arbitrary GL colour surface — any render target; the main framebuffer is not
 * the only thing a run may want on disk.
 *
 * <p>The path is the one {@link FrameCapture} uses for the main framebuffer: bind the colour texture,
 * download it, flip it (framebuffers are bottom-up, PNG is top-down). Reading a colour texture rather
 * than the swap chain is what lets a background or hidden window capture at all.
 *
 * <p><b>Port note.</b> The upstream harness also described a CloudLib in-world panel's own
 * offscreen surface; that branch is not here, because it needed the CloudLib render API. Anything
 * that is not a Minecraft {@link RenderTarget} is refused ({@code null}) rather than guessed at.
 *
 * <p>A surface that was never rendered (texture 0, or no size) yields {@code null}, and the caller
 * logs the skip — a capture may never break a run.
 */
public final class SurfaceCapture {

    private SurfaceCapture() {}

    /**
     * Downloads whatever {@code surface} names.
     *
     * @param surface a {@link RenderTarget}, or {@code null}
     * @return the downloaded image, or {@code null} when the shape is unknown or the surface has no
     *         texture yet
     */
    public static @Nullable NativeImage grab(@Nullable Object surface) {
        if (surface instanceof RenderTarget target) {
            return grab(target);
        }
        return null;
    }

    /** Downloads a render target's colour texture at the target's pixel size. */
    public static @Nullable NativeImage grab(RenderTarget target) {
        return grab(target.getColorTextureId(), target.width, target.height);
    }

    /**
     * Downloads one colour texture.
     *
     * @return the top-down image, or {@code null} when the size is not positive or the texture is unset
     */
    public static @Nullable NativeImage grab(int colorTextureId, int width, int height) {
        if (colorTextureId == 0 || width <= 0 || height <= 0) {
            return null;
        }
        NativeImage image = new NativeImage(width, height, false);
        RenderSystem.bindTexture(colorTextureId);
        image.downloadTexture(0, true);
        image.flipY();
        return image;
    }
}
