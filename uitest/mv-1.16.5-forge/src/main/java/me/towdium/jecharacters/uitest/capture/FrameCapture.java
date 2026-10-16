package me.towdium.jecharacters.uitest.capture;

import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FrameCapture implements AutoCloseable {

    private static final int uniformSampleSteps = 32;

    private final NativeImage image;
    private final int guiWidth;
    private final int guiHeight;

    private FrameCapture(NativeImage image, int guiWidth, int guiHeight) {
        this.image = image;
        this.guiWidth = guiWidth;
        this.guiHeight = guiHeight;
    }

    public static @Nullable FrameCapture grab() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }
        Framebuffer target = minecraft.getMainRenderTarget();
        if (target == null) {
            return null;
        }
        return grab(target);
    }

    public static @Nullable FrameCapture grab(Framebuffer target) {
        NativeImage image = SurfaceCapture.grab(target);
        if (image == null) {
            return null;
        }
        MainWindow window = Minecraft.getInstance().getWindow();
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

    public static boolean write(NativeImage image, Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        image.writeToFile(path);
        return isUniform(image);
    }

    @Override
    public void close() {
        image.close();
    }
}
