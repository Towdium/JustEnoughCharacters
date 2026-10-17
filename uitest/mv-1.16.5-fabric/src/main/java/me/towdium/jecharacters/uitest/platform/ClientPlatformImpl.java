package me.towdium.jecharacters.uitest.platform;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.lwjgl.glfw.GLFW;

public final class ClientPlatformImpl implements ClientPlatform {

    @Override
    public String minecraftVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    @Override
    public long windowHandle(Object window) {
        return ((Window) window).getWindow();
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
    public void hideWindow() {
        GLFW.glfwHideWindow(windowHandle(Minecraft.getInstance().getWindow()));
    }

    @Override
    public void resizeWindow(int width, int height) {
        GLFW.glfwSetWindowSize(windowHandle(Minecraft.getInstance().getWindow()), width, height);
    }


    @Override
    public Object mainRenderTarget() {
        return Minecraft.getInstance().getMainRenderTarget();
    }

    @Override
    public int renderTargetWidth() {
        return Minecraft.getInstance().getMainRenderTarget().width;
    }

    @Override
    public int renderTargetHeight() {
        return Minecraft.getInstance().getMainRenderTarget().height;
    }

    @Override
    public Object overlay() {
        return Minecraft.getInstance().getOverlay();
    }

    @Override
    public void setHideGui(boolean hidden) {
        Minecraft.getInstance().options.hideGui = hidden;
    }

    @Override
    public Object screen() {
        return Minecraft.getInstance().screen;
    }

    @Override
    public void setScreen(Object screen) {
        Minecraft.getInstance().setScreen((Screen) screen);
    }

    @Override
    public boolean titleScreenReady(Object screen) {
        return screen instanceof TitleScreen;
    }

    @Override
    public boolean skipOnboarding(Object screen) {
        return false;
    }

    @Override
    public void disconnectLevel(Object level) {
        ((ClientLevel) level).disconnect();
    }
}
