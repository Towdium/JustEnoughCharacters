package me.towdium.jecharacters.uitest.platform;

import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.SharedConstants;
import org.lwjgl.glfw.GLFW;

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
