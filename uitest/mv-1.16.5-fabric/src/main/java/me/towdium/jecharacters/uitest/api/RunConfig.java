package me.towdium.jecharacters.uitest.api;

import java.nio.file.Path;
import java.util.Collection;
import java.nio.file.Paths;

public final class RunConfig {

    private final String selection;
    private final String exclude;
    private final Path outDir;
    private final int guiScale;
    private final int windowWidth;
    private final int windowHeight;
    private final InputMode inputMode;
    private final int watchdogSeconds;
    private final boolean keepOpen;
    private final boolean headless;
    private final int scenarioTimeoutMs;
    private final int defaultSettleMs;
    private final int defaultTimeoutMs;
    private final boolean captureOnFailure;
    private final ErrorPolicy errorPolicy;

    public RunConfig(
        String selection,
        String exclude,
        Path outDir,
        int guiScale,
        int windowWidth,
        int windowHeight,
        InputMode inputMode,
        int watchdogSeconds,
        boolean keepOpen,
        boolean headless,
        int scenarioTimeoutMs,
        int defaultSettleMs,
        int defaultTimeoutMs,
        boolean captureOnFailure,
        ErrorPolicy errorPolicy) {
        this.selection = selection;
        this.exclude = exclude;
        this.outDir = outDir;
        this.guiScale = guiScale;
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
        this.inputMode = inputMode;
        this.watchdogSeconds = watchdogSeconds;
        this.keepOpen = keepOpen;
        this.headless = headless;
        this.scenarioTimeoutMs = scenarioTimeoutMs;
        this.defaultSettleMs = defaultSettleMs;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.captureOnFailure = captureOnFailure;
        this.errorPolicy = errorPolicy;
    }

    public String selection() {
        return selection;
    }

    public String exclude() {
        return exclude;
    }

    public Path outDir() {
        return outDir;
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

    public InputMode inputMode() {
        return inputMode;
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

    public boolean captureOnFailure() {
        return captureOnFailure;
    }

    public ErrorPolicy errorPolicy() {
        return errorPolicy;
    }

    public static final String prefix = "jech.uitest.";

    public static boolean requested() {
        return System.getProperty(prefix + "run") != null;
    }

    public static RunConfig fromSystemProperties() {
        Path outDir = Paths.get(stringProperty("out", "jech-uitest"));
        return new RunConfig(
            stringProperty("run", "all"),
            stringProperty("exclude", ""),
            outDir,
            intProperty("guiScale", 2),
            intProperty("windowWidth", 0),
            intProperty("windowHeight", 0),
            InputMode.fromString(stringProperty("inputMode", "SYNTHETIC")),
            intProperty("watchdogSec", 90),
            Boolean.parseBoolean(stringProperty("keepOpen", "false")),
            Boolean.parseBoolean(stringProperty("headless", "false")),
            intProperty("scenarioTimeoutMs", 120_000),
            intProperty("settleMs", 60),
            intProperty("timeoutMs", 5_000),
            !"false".equalsIgnoreCase(stringProperty("captureOnFailure", "true")),
            ErrorPolicy.fromString(stringProperty("errorPolicy", "ABORT_SCENARIO"))
        );
    }

    public static RunConfig interactive(String selection) {
        return new RunConfig(
            selection,
            "",
            Paths.get("jech-uitest"),
            2,
            0,
            0,
            InputMode.synthetic,
            90,
            true,
            false,
            120_000,
            60,
            5_000,
            true,
            ErrorPolicy.abortScenario
        );
    }

    public ScenarioSelection selectionMatcher() {
        return ScenarioSelection.parse(selection, exclude);
    }

    public int windowWidthOrDefault() {
        return windowWidth > 0 ? windowWidth : 1280;
    }

    public int windowHeightOrDefault() {
        return windowHeight > 0 ? windowHeight : 720;
    }

    public boolean selects(String name, Collection<String> tags, String group, Class<?> type) {
        return selectionMatcher().matches(name, tags, group, type);
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
