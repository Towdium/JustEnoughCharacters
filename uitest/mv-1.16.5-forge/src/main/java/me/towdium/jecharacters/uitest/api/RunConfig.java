package me.towdium.jecharacters.uitest.api;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Objects;

public final class RunConfig {

    public static final String prefix = "jech.uitest.";

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
        ErrorPolicy errorPolicy
    ) {
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

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RunConfig)) return false;
        RunConfig that = (RunConfig) other;
        return guiScale == that.guiScale
            && windowWidth == that.windowWidth
            && windowHeight == that.windowHeight
            && watchdogSeconds == that.watchdogSeconds
            && keepOpen == that.keepOpen
            && headless == that.headless
            && scenarioTimeoutMs == that.scenarioTimeoutMs
            && defaultSettleMs == that.defaultSettleMs
            && defaultTimeoutMs == that.defaultTimeoutMs
            && captureOnFailure == that.captureOnFailure
            && Objects.equals(selection, that.selection)
            && Objects.equals(exclude, that.exclude)
            && Objects.equals(outDir, that.outDir)
            && inputMode == that.inputMode
            && errorPolicy == that.errorPolicy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            selection,
            exclude,
            outDir,
            guiScale,
            windowWidth,
            windowHeight,
            inputMode,
            watchdogSeconds,
            keepOpen,
            headless,
            scenarioTimeoutMs,
            defaultSettleMs,
            defaultTimeoutMs,
            captureOnFailure,
            errorPolicy);
    }

    @Override
    public String toString() {
        return "RunConfig[selection=" + selection
            + ", exclude=" + exclude
            + ", outDir=" + outDir
            + ", guiScale=" + guiScale
            + ", windowWidth=" + windowWidth
            + ", windowHeight=" + windowHeight
            + ", inputMode=" + inputMode
            + ", watchdogSeconds=" + watchdogSeconds
            + ", keepOpen=" + keepOpen
            + ", headless=" + headless
            + ", scenarioTimeoutMs=" + scenarioTimeoutMs
            + ", defaultSettleMs=" + defaultSettleMs
            + ", defaultTimeoutMs=" + defaultTimeoutMs
            + ", captureOnFailure=" + captureOnFailure
            + ", errorPolicy=" + errorPolicy
            + "]";
    }
}
