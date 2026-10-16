package me.towdium.jecharacters.uitest.api;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class ScenarioOptions {

    private final @Nullable Integer guiScale;

    private final boolean hideGui;

    private final int settleMs;

    private final int defaultTimeoutMs;

    private final int scenarioTimeoutMs;

    private final boolean captureOnFailure;

    private final Session session;

    public ScenarioOptions(
        @Nullable Integer guiScale,
        boolean hideGui,
        int settleMs,
        int defaultTimeoutMs,
        int scenarioTimeoutMs,
        boolean captureOnFailure,
        Session session
    ) {
        this.guiScale = guiScale;
        this.hideGui = hideGui;
        this.settleMs = settleMs;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.scenarioTimeoutMs = scenarioTimeoutMs;
        this.captureOnFailure = captureOnFailure;
        this.session = session;
    }

    public @Nullable Integer guiScale() {
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

    public boolean captureOnFailure() {
        return captureOnFailure;
    }

    public Session session() {
        return session;
    }

    public static ScenarioOptions defaults() {
        return new ScenarioOptions(null, false, 60, 5_000, 120_000, true, Session.shared);
    }

    public int guiScale(int fallback) {
        return guiScale == null ? fallback : guiScale;
    }

    public int settleMs(int fallback) {
        return settleMs > 0 ? settleMs : fallback;
    }

    public int defaultTimeoutMs(int fallback) {
        return defaultTimeoutMs > 0 ? defaultTimeoutMs : fallback;
    }

    public int scenarioTimeoutMs(int fallback) {
        return scenarioTimeoutMs > 0 ? scenarioTimeoutMs : fallback;
    }

    public boolean captureOnFailure(boolean runAllows) {
        return captureOnFailure && runAllows;
    }

    public ScenarioOptions withGuiScale(@Nullable Integer scale) {
        return new ScenarioOptions(
            scale,
            hideGui,
            settleMs,
            defaultTimeoutMs,
            scenarioTimeoutMs,
            captureOnFailure,
            session
        );
    }

    public ScenarioOptions withHideGui(boolean hide) {
        return new ScenarioOptions(
            guiScale,
            hide,
            settleMs,
            defaultTimeoutMs,
            scenarioTimeoutMs,
            captureOnFailure,
            session
        );
    }

    public ScenarioOptions withSettleMs(int ms) {
        return new ScenarioOptions(
            guiScale,
            hideGui,
            ms,
            defaultTimeoutMs,
            scenarioTimeoutMs,
            captureOnFailure,
            session
        );
    }

    public ScenarioOptions withDefaultTimeoutMs(int ms) {
        return new ScenarioOptions(guiScale, hideGui, settleMs, ms, scenarioTimeoutMs, captureOnFailure, session);
    }

    public ScenarioOptions withScenarioTimeoutMs(int ms) {
        return new ScenarioOptions(guiScale, hideGui, settleMs, defaultTimeoutMs, ms, captureOnFailure, session);
    }

    public ScenarioOptions withCaptureOnFailure(boolean capture) {
        return new ScenarioOptions(guiScale, hideGui, settleMs, defaultTimeoutMs, scenarioTimeoutMs, capture, session);
    }

    public ScenarioOptions withSession(Session tier) {
        return new ScenarioOptions(
            guiScale,
            hideGui,
            settleMs,
            defaultTimeoutMs,
            scenarioTimeoutMs,
            captureOnFailure,
            tier
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ScenarioOptions)) return false;
        ScenarioOptions that = (ScenarioOptions) other;
        return hideGui == that.hideGui
            && settleMs == that.settleMs
            && defaultTimeoutMs == that.defaultTimeoutMs
            && scenarioTimeoutMs == that.scenarioTimeoutMs
            && captureOnFailure == that.captureOnFailure
            && Objects.equals(guiScale, that.guiScale)
            && session == that.session;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            guiScale, hideGui, settleMs, defaultTimeoutMs, scenarioTimeoutMs, captureOnFailure, session);
    }

    @Override
    public String toString() {
        return "ScenarioOptions[guiScale=" + guiScale
            + ", hideGui=" + hideGui
            + ", settleMs=" + settleMs
            + ", defaultTimeoutMs=" + defaultTimeoutMs
            + ", scenarioTimeoutMs=" + scenarioTimeoutMs
            + ", captureOnFailure=" + captureOnFailure
            + ", session=" + session
            + "]";
    }
}
