package me.towdium.jecharacters.uitest.api;

import java.nio.file.Path;
import java.util.Collection;

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
 */
public record RunConfig(
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

    /** The prefix every harness system property carries. */
    public static final String prefix = "jech.uitest.";

    /** Whether a harness run was requested. */
    public static boolean requested() {
        return System.getProperty(prefix + "run") != null;
    }

    /** Reads the whole configuration from system properties. */
    public static RunConfig fromSystemProperties() {
        Path outDir = Path.of(stringProperty("out", "jech-uitest"));
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
            Path.of("jech-uitest"),
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
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad harness property " + prefix + name + "='" + value + "'", e);
        }
    }
}
