package me.towdium.jecharacters.uitest.capture;

import me.towdium.jecharacters.uitest.platform.ClientPlatform;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class FrameCapture implements AutoCloseable {

    private static final int uniformSampleSteps = 32;

    private final Object image;
    private final int guiWidth;
    private final int guiHeight;

    FrameCapture(Object image, int guiWidth, int guiHeight) {
        this.image = image;
        this.guiWidth = guiWidth;
        this.guiHeight = guiHeight;
    }

    public static boolean request(Consumer<FrameCapture> consumer) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        return CapturePlatform.current().request(
            ClientPlatform.current().mainRenderTarget(),
            image -> consumer.accept(
                new FrameCapture(
                    image,
                    minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight()
                )
            )
        );
    }

    public Object image() {
        return image;
    }

    public int guiWidth() {
        return guiWidth;
    }

    public int guiHeight() {
        return guiHeight;
    }

    public static boolean isUniform(Object image) {
        CapturePlatform platform = CapturePlatform.current();
        int width = platform.imageWidth(image);
        int height = platform.imageHeight(image);
        if (width < 2 || height < 2) {
            return false;
        }
        int stepX = Math.max(1, width / uniformSampleSteps);
        int stepY = Math.max(1, height / uniformSampleSteps);
        int reference = platform.pixel(image, 0, 0);
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                if (platform.pixel(image, x, y) != reference) {
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

    public static boolean write(Object image, Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        CapturePlatform.current().writeImage(image, path);
        return isUniform(image);
    }

    @Override
    public void close() {
        CapturePlatform.current().closeImage(image);
    }
}
