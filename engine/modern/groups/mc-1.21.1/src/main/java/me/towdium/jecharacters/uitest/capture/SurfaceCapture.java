package me.towdium.jecharacters.uitest.capture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jspecify.annotations.Nullable;

public final class SurfaceCapture {

    private SurfaceCapture() {}

    public static @Nullable NativeImage grab(@Nullable Object surface) {
        if (surface instanceof RenderTarget target) {
            return grab(target);
        }
        return null;
    }

    public static @Nullable NativeImage grab(RenderTarget target) {
        return grab(target.getColorTextureId(), target.width, target.height);
    }

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
