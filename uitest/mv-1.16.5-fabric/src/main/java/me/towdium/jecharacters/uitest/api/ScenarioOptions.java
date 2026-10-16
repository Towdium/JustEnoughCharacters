package me.towdium.jecharacters.uitest.api;

import org.jspecify.annotations.Nullable;

/**
 * Per-scenario tuning, resolved against the run configuration: a zero or absent value means "use
 * the run's".
 *
 * <p>The defaults are the proven ones: a 60 ms settle is one client tick plus a margin, because
 * CloudLib widgets tick at 20 Hz; a 5 s wait budget is long enough for a server round trip on an
 * integrated server; a 120 s scenario budget is the outer guard.
 *
 * <p><b>Port note (Java 8).</b> The 1.21.1 record of the same name, as a plain immutable class: Java
 * 8 has no records, so the fields are final, the accessors are the record components' names and
 * the public constructor is the canonical one. Nothing else changes.
 */
public final class ScenarioOptions {

    @Nullable
    private final Integer guiScale;
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
        Session session) {
        this.guiScale = guiScale;
        this.hideGui = hideGui;
        this.settleMs = settleMs;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.scenarioTimeoutMs = scenarioTimeoutMs;
        this.captureOnFailure = captureOnFailure;
        this.session = session;
    }

    @Nullable
    public Integer guiScale() {
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

    /** The values a scenario gets when its annotation says nothing. */
    public static ScenarioOptions defaults() {
        return new ScenarioOptions(null, false, 60, 5_000, 120_000, true, Session.shared);
    }

    /** The GUI scale to pin, taking the run's value when this scenario has none. */
    public int guiScale(int fallback) {
        return guiScale == null ? fallback : guiScale;
    }

    /** The settle time after each step. */
    public int settleMs(int fallback) {
        return settleMs > 0 ? settleMs : fallback;
    }

    /** The wait budget for a step that repeats itself. */
    public int defaultTimeoutMs(int fallback) {
        return defaultTimeoutMs > 0 ? defaultTimeoutMs : fallback;
    }

    /** The budget for the whole scenario. */
    public int scenarioTimeoutMs(int fallback) {
        return scenarioTimeoutMs > 0 ? scenarioTimeoutMs : fallback;
    }

    /** Whether a failing step should capture the frame it failed in; the run may veto it. */
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

    /** The session tier this scenario needs; see {@link Session}. */
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
}
