package me.towdium.jecharacters.uitest.api;

import java.nio.file.Path;
import java.util.Collection;

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
    ErrorPolicy errorPolicy,
    String worldGenerator,
    int platformLayers
) {

    public static final String prefix = "jech.uitest.";

    public RunConfig {
        if (!"void".equals(worldGenerator) && !"flat".equals(worldGenerator)) {
            throw new IllegalArgumentException(
                "bad harness property " + prefix + "worldGenerator='" + worldGenerator + "'");
        }
        if (platformLayers < 1) {
            throw new IllegalArgumentException(
                "bad harness property " + prefix + "platformLayers='" + platformLayers + "'");
        }
    }

    public static boolean requested() {
        return System.getProperty(prefix + "run") != null;
    }

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
            ErrorPolicy.fromString(stringProperty("errorPolicy", "ABORT_SCENARIO")),
            stringProperty("worldGenerator", "void"),
            intProperty("platformLayers", 1)
        );
    }

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
            ErrorPolicy.abortScenario,
            "void",
            1
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
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad harness property " + prefix + name + "='" + value + "'", e);
        }
    }
}
