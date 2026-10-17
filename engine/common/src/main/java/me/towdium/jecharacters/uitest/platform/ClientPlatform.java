package me.towdium.jecharacters.uitest.platform;

public interface ClientPlatform {

    String minecraftVersion();

    long windowHandle(Object window);

    int windowWidth(Object window);

    int windowHeight(Object window);

    void setGuiScale(int scale);

    void setRenderDistance(int distance);

    void setVsync(boolean enabled);

    void resizeDisplay();

    void hideWindow();

    void resizeWindow(int width, int height);

    Object mainRenderTarget();

    int renderTargetWidth();

    int renderTargetHeight();

    Object overlay();

    void setHideGui(boolean hidden);

    Object screen();

    void setScreen(Object screen);

    boolean titleScreenReady(Object screen);

    boolean skipOnboarding(Object screen);

    void disconnectLevel(Object level);

    default void alignJeiFocusKey() {
    }

    static void register(ClientPlatform platform) {
        Holder.platform = platform;
    }

    static ClientPlatform current() {
        return Holder.platform;
    }

    final class Holder {

        static ClientPlatform platform;
    }
}
