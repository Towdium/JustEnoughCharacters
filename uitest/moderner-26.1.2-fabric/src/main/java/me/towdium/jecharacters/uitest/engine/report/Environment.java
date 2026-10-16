package me.towdium.jecharacters.uitest.engine.report;

import java.util.List;

public record Environment(
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
    boolean headless
) {}
