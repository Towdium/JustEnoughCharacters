package me.towdium.jecharacters.uitest.capture;

import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public final class SurfaceCapture {

    private SurfaceCapture() {}

    public static boolean request(@Nullable Object surface, Consumer<Object> consumer) {
        if (surface == null) {
            return false;
        }
        return CapturePlatform.current().request(surface, consumer);
    }
}
