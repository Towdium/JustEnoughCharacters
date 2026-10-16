package me.towdium.jecharacters.uitest.api;

import java.io.File;

public final class RunConfig {

    public static final String prefix = "jech.uitest.";

    private final String selection;
    private final String exclude;
    private final File outDir;
    private final int guiScale;
    private final int watchdogSeconds;
    private final boolean keepOpen;
    private final boolean headless;
    private final int scenarioTimeoutMs;
    private final int defaultSettleMs;
    private final int defaultTimeoutMs;
    private final InputMode inputMode;

    private RunConfig(
            String selection,
            String exclude,
            File outDir,
            int guiScale,
            int watchdogSeconds,
            boolean keepOpen,
            boolean headless,
            int scenarioTimeoutMs,
            int defaultSettleMs,
            int defaultTimeoutMs,
            InputMode inputMode) {
        this.selection = selection;
        this.exclude = exclude;
        this.outDir = outDir;
        this.guiScale = guiScale;
        this.watchdogSeconds = watchdogSeconds;
        this.keepOpen = keepOpen;
        this.headless = headless;
        this.scenarioTimeoutMs = scenarioTimeoutMs;
        this.defaultSettleMs = defaultSettleMs;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.inputMode = inputMode;
    }

    public static boolean requested() {
        return System.getProperty(prefix + "run") != null;
    }

    public static RunConfig fromSystemProperties() {
        File outDir = new File(stringProperty("out", "jech-uitest"));
        return new RunConfig(
                stringProperty("run", "all"),
                stringProperty("exclude", ""),
                outDir,
                intProperty("guiScale", 2),
                intProperty("watchdogSec", 90),
                Boolean.parseBoolean(stringProperty("keepOpen", "false")),
                Boolean.parseBoolean(stringProperty("headless", "false")),
                intProperty("scenarioTimeoutMs", 120_000),
                intProperty("settleMs", 60),
                intProperty("timeoutMs", 5_000),
                InputMode.fromString(stringProperty("inputMode", "SYNTHETIC")));
    }

    public String selection() {
        return selection;
    }

    public String exclude() {
        return exclude;
    }

    public File outDir() {
        return outDir;
    }

    public int guiScale() {
        return guiScale;
    }

    public int watchdogSeconds() {
        return watchdogSeconds;
    }

    public boolean keepOpen() {
        return keepOpen;
    }

    public boolean headless() {
        return headless;
    }

    public int scenarioTimeoutMs() {
        return scenarioTimeoutMs;
    }

    public int defaultSettleMs() {
        return defaultSettleMs;
    }

    public int defaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    public InputMode inputMode() {
        return inputMode;
    }

    private static String stringProperty(String name, String fallback) {
        String value = System.getProperty(prefix + name);
        return value == null ? fallback : value;
    }

    private static int intProperty(String name, int fallback) {
        String value = System.getProperty(prefix + name);
        if (value == null || value.trim().isEmpty()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad harness property " + prefix + name + "='" + value + "'", e);
        }
    }
}
