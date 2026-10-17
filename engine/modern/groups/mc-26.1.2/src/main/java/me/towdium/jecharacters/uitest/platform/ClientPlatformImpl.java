package me.towdium.jecharacters.uitest.platform;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;

public final class ClientPlatformImpl implements ClientPlatform {

    @Override
    public String minecraftVersion() {
        return SharedConstants.getCurrentVersion().name();
    }

    @Override
    public long windowHandle(Object window) {
        return ((Window) window).handle();
    }

    @Override
    public int windowWidth(Object window) {
        return ((Window) window).getWidth();
    }

    @Override
    public int windowHeight(Object window) {
        return ((Window) window).getHeight();
    }

    @Override
    public void setGuiScale(int scale) {
        Minecraft.getInstance().options.guiScale().set(scale);
    }

    @Override
    public void setRenderDistance(int distance) {
        Minecraft.getInstance().options.renderDistance().set(distance);
    }

    @Override
    public void setVsync(boolean enabled) {
        Minecraft.getInstance().options.enableVsync().set(enabled);
    }

    @Override
    public void resizeDisplay() {
        Minecraft.getInstance().resizeGui();
    }

    @Override
    public boolean titleScreenReady(Object screen) {
        return screen instanceof TitleScreen;
    }

    @Override
    public boolean skipOnboarding(Object screen) {
        if (!(screen instanceof AccessibilityOnboardingScreen)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.onboardAccessibility = false;
        minecraft.options.save();
        minecraft.setScreen(new TitleScreen());
        return true;
    }

    @Override
    public void disconnectLevel(Object level) {
        ((ClientLevel) level).disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
    }
}
