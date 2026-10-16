package me.towdium.jecharacters.uitest.capture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One downloaded frame, plus the pixel helpers the capture pipeline works in.
 *
 * <p>The readback is of the render target's <em>colour texture</em>, never the swap chain, so
 * whether the window is focused, occluded or even shown does not matter — which is what makes
 * background and headless runs capture correctly. One download serves the full frame and the
 * whole-frame pixel sampling of a run.
 *
 * <p>The framebuffer is bottom-up and PNG is top-down, hence the {@link NativeImage#flipY()} after
 * the download.
 *
 * <p><b>Port note.</b> The upstream crop helpers ({@code cropRect}/{@code crop}) are not here: a
 * crop is addressed by a CloudLib widget rectangle, and this harness carries no widget tree. The
 * download, the flat-colour probe and the atomic-ish write are the parts that are.
 */
public final class FrameCapture implements AutoCloseable {

    /** Steps per axis when sampling a frame for the flat-colour check, so at most 1024 pixels. */
    private static final int uniformSampleSteps = 32;

    private final NativeImage image;
    private final int guiWidth;
    private final int guiHeight;

    private FrameCapture(NativeImage image, int guiWidth, int guiHeight) {
        this.image = image;
        this.guiWidth = guiWidth;
        this.guiHeight = guiHeight;
    }

    /**
     * Downloads the main render target. Returns {@code null} when there is no client to read from,
     * so a capture can never break a run that is already shutting down.
     */
    public static @Nullable FrameCapture grab() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }
        RenderTarget target = minecraft.getMainRenderTarget();
        if (target == null) {
            return null;
        }
        return grab(target);
    }

    /**
     * Downloads an arbitrary render target, carrying the window's GUI scale so a later crop of it
     * could map back to the coordinates a scenario works in.
     */
    public static @Nullable FrameCapture grab(RenderTarget target) {
        NativeImage image = SurfaceCapture.grab(target);
        if (image == null) {
            return null;
        }
        Window window = Minecraft.getInstance().getWindow();
        return new FrameCapture(image, window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    public NativeImage image() {
        return image;
    }

    public int guiWidth() {
        return guiWidth;
    }

    public int guiHeight() {
        return guiHeight;
    }

    /**
     * Whether every sampled pixel equals the top-left one, which usually means the readback came
     * from a framebuffer that was never drawn to. Sampling a coarse grid keeps this affordable on
     * the render thread — a 4K frame is eight million pixels.
     */
    public static boolean isUniform(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width < 2 || height < 2) {
            return false;
        }
        int stepX = Math.max(1, width / uniformSampleSteps);
        int stepY = Math.max(1, height / uniformSampleSteps);
        int reference = image.getPixelRGBA(0, 0);
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                if (image.getPixelRGBA(x, y) != reference) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The pixel half of {@link #isUniform(NativeImage)}, in row-major ARGB order. */
    public static boolean isUniform(int[] pixels, int width, int height) {
        if (width < 2 || height < 2 || pixels.length < width * height) {
            return false;
        }
        int stepX = Math.max(1, width / uniformSampleSteps);
        int stepY = Math.max(1, height / uniformSampleSteps);
        int reference = pixels[0];
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                if (pixels[y * width + x] != reference) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Writes {@code image} to {@code path}, creating the parent directory.
     *
     * @return the {@link #isUniform(NativeImage)} verdict, surfaced as the capture's suspect flag
     */
    public static boolean write(NativeImage image, Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        image.writeToFile(path);
        return isUniform(image);
    }

    /**
     * Releases the downloaded image. A frame is used in a try-with-resources statement, so a close
     * that throws is caught by the same {@code catch} the capture itself uses — a screenshot may
     * never take a run down with it.
     */
    @Override
    public void close() {
        image.close();
    }
}
