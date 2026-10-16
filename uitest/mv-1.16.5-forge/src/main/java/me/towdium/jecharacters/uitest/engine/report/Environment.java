package me.towdium.jecharacters.uitest.engine.report;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Environment {

    private final String minecraft;

    private final String neoforge;

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
        String neoforge,
        String java,
        String os,
        List<String> mods,
        int guiScale,
        int windowWidth,
        int windowHeight,
        int framebufferWidth,
        int framebufferHeight,
        String inputMode,
        boolean headless
    ) {
        this.minecraft = minecraft;
        this.neoforge = neoforge;
        this.java = java;
        this.os = os;
        this.mods = mods;
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

    public String neoforge() {
        return neoforge;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Environment)) return false;
        Environment that = (Environment) other;
        return guiScale == that.guiScale
            && windowWidth == that.windowWidth
            && windowHeight == that.windowHeight
            && framebufferWidth == that.framebufferWidth
            && framebufferHeight == that.framebufferHeight
            && headless == that.headless
            && Objects.equals(minecraft, that.minecraft)
            && Objects.equals(neoforge, that.neoforge)
            && Objects.equals(java, that.java)
            && Objects.equals(os, that.os)
            && Objects.equals(mods, that.mods)
            && Objects.equals(inputMode, that.inputMode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            minecraft,
            neoforge,
            java,
            os,
            mods,
            guiScale,
            windowWidth,
            windowHeight,
            framebufferWidth,
            framebufferHeight,
            inputMode,
            headless);
    }

    @Override
    public String toString() {
        return "Environment[minecraft=" + minecraft
            + ", neoforge=" + neoforge
            + ", java=" + java
            + ", os=" + os
            + ", mods=" + mods
            + ", guiScale=" + guiScale
            + ", windowWidth=" + windowWidth
            + ", windowHeight=" + windowHeight
            + ", framebufferWidth=" + framebufferWidth
            + ", framebufferHeight=" + framebufferHeight
            + ", inputMode=" + inputMode
            + ", headless=" + headless
            + "]";
    }
}
