package me.towdium.jecharacters.uitest.capture;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.shader.Framebuffer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class CapturePlatformImpl implements CapturePlatform {

    @Override
    public boolean request(Object target, Consumer<Object> consumer) {
        if (!(target instanceof Framebuffer)) {
            return false;
        }
        Framebuffer framebuffer = (Framebuffer) target;
        int colorTextureId = framebuffer.getColorTextureId();
        if (colorTextureId == 0 || framebuffer.width <= 0 || framebuffer.height <= 0) {
            return false;
        }
        NativeImage image = new NativeImage(framebuffer.width, framebuffer.height, false);
        RenderSystem.bindTexture(colorTextureId);
        image.downloadTexture(0, true);
        image.flipY();
        consumer.accept(image);
        return true;
    }

    @Override
    public int imageWidth(Object image) {
        return ((NativeImage) image).getWidth();
    }

    @Override
    public int imageHeight(Object image) {
        return ((NativeImage) image).getHeight();
    }

    @Override
    public int pixel(Object image, int x, int y) {
        return ((NativeImage) image).getPixelRGBA(x, y);
    }

    @Override
    public void writeImage(Object image, Path path) throws IOException {
        ((NativeImage) image).writeToFile(path);
    }

    @Override
    public void closeImage(Object image) {
        ((NativeImage) image).close();
    }
}
