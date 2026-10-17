package me.towdium.jecharacters.uitest.platform;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import me.towdium.jecharacters.mixin.uitest.KeyMappingAccessor;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientPlatformImpl implements ClientPlatform {

    private static final Logger log = LoggerFactory.getLogger("jechtest-uitest");

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

    @Override
    public void alignJeiFocusKey() {
        KeyMapping mapping = KeyMappingAccessor.jechtestAll().get("key.jei.focusSearch");
        if (mapping == null) {
            log.warn("[uitest] no 'key.jei.focusSearch' mapping is registered; JEI scenarios will not find the search bar");
            return;
        }
        if (mapping.isUnbound()) {
            InputConstants.Key key = InputConstants.getKey("key.keyboard.f");
            if (key != null) {
                mapping.setKey(key);
                log.info("[uitest] pinned JEI's focus-search bind (was unbound on this loader) to key.keyboard.f");
            }
        }
    }
}
