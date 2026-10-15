package me.towdium.jecharacters.uitest.engine.report;

import java.util.Collections;
import java.util.List;

/**
 * The machine a run happened on and the client settings it ran with — enough to tell two runs apart
 * when their results disagree.
 *
 * <p><b>Port note.</b> The 1.21.1 record as a plain immutable class, one field renamed: the loader's
 * version is {@code forge} here rather than {@code neoforge}, because on the 1.12.2 line the loader
 * the report names is Forge 14.23.5.2864. The two framebuffer/window pairs and the input route keep
 * the meaning they have on every other checkout, so a reader of both reports compares the same
 * columns.
 */
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

    /** The Minecraft version name. */
    public String minecraft() {
        return minecraft;
    }

    /** The Forge version. */
    public String forge() {
        return forge;
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
}
