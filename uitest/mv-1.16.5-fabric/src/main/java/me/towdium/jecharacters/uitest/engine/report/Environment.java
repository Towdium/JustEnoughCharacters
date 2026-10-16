package me.towdium.jecharacters.uitest.engine.report;

import java.util.List;

/**
 * The machine a run happened on and the client settings it ran with — enough to tell two runs apart
 * when their results disagree.
 *
 * <p><b>Port note.</b> The 1.21.1 record of the same name, as a plain immutable class: Java 8 has
 * no records, so the fields are final, the accessors are the record components' names and the
 * public constructor is the canonical one. Nothing else changes.
 *
 * @param minecraft        the Minecraft version name
 * @param fabric           the Fabric Loader version; the loader-version member is the one
 *                         loader-specific field of this record, so each loader's copy of the harness
 *                         names it after that loader (the NeoForge copy calls it {@code neoforge})
 * @param java             the JVM version string
 * @param os               the operating system
 * @param mods             every loaded mod id, sorted
 * @param guiScale         the GUI scale the run was pinned to
 * @param windowWidth      the window width in physical pixels
 * @param windowHeight     the window height in physical pixels
 * @param framebufferWidth the framebuffer width
 * @param framebufferHeight the framebuffer height
 * @param inputMode        the input route the run used
 * @param headless         whether the window was hidden
 */
public final class Environment {

    private final String minecraft;
    private final String fabric;
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
        String fabric,
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
        this.fabric = fabric;
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

    /** The Fabric Loader version. */
    public String fabric() {
        return fabric;
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
