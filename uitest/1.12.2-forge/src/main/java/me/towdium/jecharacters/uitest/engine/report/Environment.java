package me.towdium.jecharacters.uitest.engine.report;

import java.util.Collections;
import java.util.List;

public final class Environment {

    private final String minecraft;
    private final String forge;
    private final String java;
    private final String os;
    private final List<String> mods;
    private final int guiScale;
    private final int windowWidth;
    private final int windowHeight;
    private final int framebufferWidth;
    private final int framebufferHeight;
    private final String inputMode;
    private final boolean headless;

    public Environment(
            String minecraft,
            String forge,
            String java,
            String os,
            List<String> mods,
            int guiScale,
            int windowWidth,
            int windowHeight,
            int framebufferWidth,
            int framebufferHeight,
            String inputMode,
            boolean headless) {
        this.minecraft = minecraft;
        this.forge = forge;
        this.java = java;
        this.os = os;
        this.mods = Collections.unmodifiableList(new java.util.ArrayList<String>(mods));
        this.guiScale = guiScale;
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
        this.inputMode = inputMode;
        this.headless = headless;
    }

    public String minecraft() {
        return minecraft;
    }

    public String forge() {
        return forge;
    }

    public String java() {
        return java;
    }

    public String os() {
        return os;
    }

    public List<String> mods() {
        return mods;
    }

    public int guiScale() {
        return guiScale;
    }

    public int windowWidth() {
        return windowWidth;
    }

    public int windowHeight() {
        return windowHeight;
    }

    public int framebufferWidth() {
        return framebufferWidth;
    }

    public int framebufferHeight() {
        return framebufferHeight;
    }

    public String inputMode() {
        return inputMode;
    }

    public boolean headless() {
        return headless;
    }
}
