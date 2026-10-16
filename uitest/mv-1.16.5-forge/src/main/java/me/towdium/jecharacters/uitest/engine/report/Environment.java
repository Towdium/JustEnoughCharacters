package me.towdium.jecharacters.uitest.engine.report;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The machine a run happened on and the client settings it ran with — enough to tell two runs apart
 * when their results disagree.
 *
 * <p><b>Port note.</b> The 1.21.1 record as a plain immutable class: Java 8 has no records, so the
 * twelve components are private final fields, the constructor is the canonical one, and the
 * accessors keep the component names, which is also what Gson writes, so the JSON block is the one
 * every other checkout reads. {@code equals}, {@code hashCode} and {@code toString} are spelled out
 * component for component, because an environment is a value. The component list is stored as the
 * record stored it, without a defensive copy: the one caller hands over a {@code Stream.collect(Collectors.collect(Collectors.toList()))}
 * result, so a copy would only be a second immutable list.
 */
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

    /** The Minecraft version name. */
    public String minecraft() {
        return minecraft;
    }

    /** The NeoForge version. */
    public String neoforge() {
        return neoforge;
    }

    /** The JVM version string. */
    public String java() {
        return java;
    }

    /** The operating system. */
    public String os() {
        return os;
    }

    /** Every loaded mod id, sorted. */
    public List<String> mods() {
        return mods;
    }

    /** The GUI scale the run was pinned to. */
    public int guiScale() {
        return guiScale;
    }

    /** The window width in physical pixels. */
    public int windowWidth() {
        return windowWidth;
    }

    /** The window height in physical pixels. */
    public int windowHeight() {
        return windowHeight;
    }

    /** The framebuffer width. */
    public int framebufferWidth() {
        return framebufferWidth;
    }

    /** The framebuffer height. */
    public int framebufferHeight() {
        return framebufferHeight;
    }

    /** The input route the run used. */
    public String inputMode() {
        return inputMode;
    }

    /** Whether the window was hidden. */
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
