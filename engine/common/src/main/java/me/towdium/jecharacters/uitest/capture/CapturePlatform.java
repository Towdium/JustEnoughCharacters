package me.towdium.jecharacters.uitest.capture;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public interface CapturePlatform {

    boolean request(Object target, Consumer<Object> image);

    int imageWidth(Object image);

    int imageHeight(Object image);

    int pixel(Object image, int x, int y);

    void writeImage(Object image, Path path) throws IOException;

    void closeImage(Object image);

    static void register(CapturePlatform platform) {
        Holder.platform = platform;
    }

    static CapturePlatform current() {
        return Holder.platform;
    }

    final class Holder {

        static CapturePlatform platform;
    }
}
