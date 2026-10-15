package me.towdium.jecharacters.uitest.api;

/**
 * The per-scenario tuning a scenario inherits from its {@link ClientScenario} annotation and may
 * override while it is built.
 *
 * <p><b>Port note.</b> This is the 1.12.2 subset of the 1.21.1 record of the same name: the fields
 * the smoke path needs (GUI scale, HUD visibility, the two wait budgets and the settle) are here,
 * and the session tier is not yet, because the 1.12.2 runner in this wave always uses the world it
 * created for the whole run. Immutable, with {@code with*} copies, exactly like the original.
 */
public final class ScenarioOptions {

    private final int guiScale;
    private final boolean hideGui;
    private final int settleMs;
    private final int defaultTimeoutMs;
    private final int scenarioTimeoutMs;

    private ScenarioOptions(int guiScale, boolean hideGui, int settleMs, int defaultTimeoutMs, int scenarioTimeoutMs) {
        this.guiScale = guiScale;
        this.hideGui = hideGui;
        this.settleMs = settleMs;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.scenarioTimeoutMs = scenarioTimeoutMs;
    }

    /** The defaults every scenario starts from. */
    public static ScenarioOptions defaults() {
        return new ScenarioOptions(2, false, 60, 5_000, 120_000);
    }

    public int guiScale() {
        return guiScale;
    }

    public boolean hideGui() {
        return hideGui;
    }

    public int settleMs() {
        return settleMs;
    }

    public int defaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    public int scenarioTimeoutMs() {
        return scenarioTimeoutMs;
    }

    public ScenarioOptions withGuiScale(int value) {
        return new ScenarioOptions(value, hideGui, settleMs, defaultTimeoutMs, scenarioTimeoutMs);
    }

    public ScenarioOptions withHideGui(boolean value) {
        return new ScenarioOptions(guiScale, value, settleMs, defaultTimeoutMs, scenarioTimeoutMs);
    }

    public ScenarioOptions withSettleMs(int value) {
        return new ScenarioOptions(guiScale, hideGui, value, defaultTimeoutMs, scenarioTimeoutMs);
    }

    public ScenarioOptions withDefaultTimeoutMs(int value) {
        return new ScenarioOptions(guiScale, hideGui, settleMs, value, scenarioTimeoutMs);
    }

    public ScenarioOptions withScenarioTimeoutMs(int value) {
        return new ScenarioOptions(guiScale, hideGui, settleMs, defaultTimeoutMs, value);
    }
}
