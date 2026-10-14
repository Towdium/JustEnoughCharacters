package me.towdium.jecharacters.uitest.engine.report;

import java.util.List;

/**
 * The machine a run happened on and the client settings it ran with — enough to tell two runs apart
 * when their results disagree.
 *
 * @param minecraft        the Minecraft version name
 * @param neoforge         the NeoForge version
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
public record Environment(
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
) {}
