package me.towdium.jecharacters.uitest.platform;

import java.util.List;

public interface Platform {

    String loaderLabel();

    String loaderVersion();

    List<String> modIds();

    boolean developmentEnvironment();

    static void register(Platform platform) {
        Holder.platform = platform;
    }

    static Platform current() {
        return Holder.platform;
    }

    final class Holder {

        static Platform platform;
    }
}
