package me.towdium.jecharacters.uitest.capture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public final class SurfaceCapture {

    private SurfaceCapture() {}

    public static boolean request(@Nullable Object surface, Consumer<NativeImage> consumer) {
        if (surface instanceof RenderTarget target) {
            return request(target, consumer);
        }
        return false;
    }

    public static boolean request(RenderTarget target, Consumer<NativeImage> consumer) {
        if (target.width <= 0 || target.height <= 0 || target.getColorTexture() == null) {
            return false;
        }
        Screenshot.takeScreenshot(target, consumer);
        return true;
    }
}
