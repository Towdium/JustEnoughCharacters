package me.towdium.jecharacters.uitest.capture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class CapturePlatformImpl implements CapturePlatform {

    @Override
    public boolean request(Object target, Consumer<Object> consumer) {
        if (!(target instanceof RenderTarget)) {
            return false;
        }
        RenderTarget renderTarget = (RenderTarget) target;
        int colorTextureId = renderTarget.getColorTextureId();
        if (colorTextureId == 0 || renderTarget.width <= 0 || renderTarget.height <= 0) {
            return false;
        }
        NativeImage image = new NativeImage(renderTarget.width, renderTarget.height, false);
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
