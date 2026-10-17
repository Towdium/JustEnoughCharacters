package me.towdium.jecharacters.uitest.platform;

import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.SharedConstants;

public final class ClientPlatformImpl implements ClientPlatform {

    @Override
    public String minecraftVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    @Override
    public long windowHandle(Object window) {
        return ((MainWindow) window).getWindow();
    }

    @Override
    public int windowWidth(Object window) {
        return ((MainWindow) window).getWidth();
    }

    @Override
    public int windowHeight(Object window) {
        return ((MainWindow) window).getHeight();
    }

    @Override
    public void setGuiScale(int scale) {
        Minecraft.getInstance().options.guiScale = scale;
    }

    @Override
    public void setRenderDistance(int distance) {
        Minecraft.getInstance().options.renderDistance = distance;
    }

    @Override
    public void setVsync(boolean enabled) {
        Minecraft.getInstance().options.enableVsync = enabled;
    }

    @Override
    public void resizeDisplay() {
        Minecraft.getInstance().resizeDisplay();
    }

    @Override
    public boolean titleScreenReady(Object screen) {
        return screen instanceof MainMenuScreen;
    }

    @Override
    public boolean skipOnboarding(Object screen) {
        return false;
    }

    @Override
    public void disconnectLevel(Object level) {
        ((ClientWorld) level).disconnect();
    }
}
