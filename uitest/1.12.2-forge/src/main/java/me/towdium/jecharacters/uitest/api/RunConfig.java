package me.towdium.jecharacters.uitest.api;

import java.io.File;

/**
 * Everything a run needs that is not a scenario: what to select, where to write, and the budgets the
 * scenarios inherit.
 *
 * <p>Read from system properties rather than program arguments, because Minecraft's own argument
 * parser rejects arguments it does not know. {@link #requested()} is the single switch that decides
 * whether the harness does anything at all: without {@code -Djech.uitest.run} the frame pump returns
 * immediately and an ordinary client run is untouched.
 *
 * <p><b>Port note.</b> The property prefix is {@code jech.uitest.}, the same one every other checkout
 * uses, because the jt gradle backend spells the switches the same way for every line
 * (compat-tests/lib/instance.py, {@code gradle_command}). The upstream multi-process block and the
 * golden-image switches are not here: neither the MP orchestrator nor the golden comparators are
 * ported.
 */
public final class RunConfig {

    /** The prefix every harness system property carries. */
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

    /** Whether a harness run was requested. */
    public static boolean requested() {
        return System.getProperty(prefix + "run") != null;
    }

    /** Reads the whole configuration from system properties. */
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
