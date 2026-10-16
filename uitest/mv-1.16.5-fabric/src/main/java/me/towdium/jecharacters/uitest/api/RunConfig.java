package me.towdium.jecharacters.uitest.api;

import java.nio.file.Path;
import java.util.Collection;
import java.nio.file.Paths;

/**
 * Everything a run needs that is not a scenario: what to select, where to write, and the budgets the
 * scenarios inherit.
 *
 * <p>Read from system properties rather than program arguments, because Minecraft's own argument
 * parser rejects arguments it does not know. {@link #requested()} is the single switch that decides
 * whether the harness does anything at all: without {@code -Djech.uitest.run} the frame pump returns
 * immediately and an ordinary client run is untouched.
 *
 * <p><b>Port note.</b> The multi-process block of the upstream CloudLib harness ({@code role}, hub
 * address, per-process output directory, and the whole multi-process property namespace) and
 * {@code updateGolden} are not here: the MP orchestrator and the golden-image comparators are not
 * ported, so there is nothing for them to configure. The property prefix is {@code jech.uitest.}.
 *
 * <p><b>Port note (Java 8).</b> The 1.21.1 record of the same name, as a plain immutable class: Java
 * 8 has no records, so the fields are final, the accessors are the record components' names and
 * the public constructor is the canonical one. Nothing else changes.
 */
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

    /** The prefix every harness system property carries. */
    public static final String prefix = "jech.uitest.";

    /** Whether a harness run was requested. */
    public static boolean requested() {
        return System.getProperty(prefix + "run") != null;
    }

    /** Reads the whole configuration from system properties. */
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

    /**
     * The configuration the in-game {@code /jechtest run} entry point uses.
     *
     * <p>Keep-open is the point of an interactive run: it runs against the world the player already
     * has loaded, never creates one, leaves the window and the HUD alone, and hands the client back
     * to the player when the queue is done instead of stopping it.
     */
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

    /** The selection, parsed. */
    public ScenarioSelection selectionMatcher() {
        return ScenarioSelection.parse(selection, exclude);
    }

    /** The window size, or {@code 0,0} when the runner should size the window itself. */
    public int windowWidthOrDefault() {
        return windowWidth > 0 ? windowWidth : 1280;
    }

    /** The window height, or the default when none was given. */
    public int windowHeightOrDefault() {
        return windowHeight > 0 ? windowHeight : 720;
    }

    /** Whether a scenario would be selected. */
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
