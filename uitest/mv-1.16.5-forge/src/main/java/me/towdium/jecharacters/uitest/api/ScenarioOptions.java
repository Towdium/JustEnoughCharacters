package me.towdium.jecharacters.uitest.api;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Per-scenario tuning, resolved against the run configuration: a zero or absent value means "use
 * the run's".
 *
 * <p>The defaults are the proven ones: a 60 ms settle is one client tick plus a margin, because
 * CloudLib widgets tick at 20 Hz; a 5 s wait budget is long enough for a server round trip on an
 * integrated server; a 120 s scenario budget is the outer guard.
 *
 * <p><b>Port note.</b> The 1.21.1 record as a plain immutable class: Java 8 has no records, so the
 * seven components are private final fields, the constructor is the canonical one, and the accessors
 * keep the component names — including {@code hideGui()}, {@code session()} and the no-argument
 * {@code guiScale()}, {@code settleMs()}, {@code defaultTimeoutMs()}, {@code scenarioTimeoutMs()}
 * and {@code captureOnFailure()}, which the record generated implicitly beside the one-argument
 * resolving forms declared here. {@code equals}, {@code hashCode} and {@code toString} are spelled
 * out component for component, because an options set is a value.
 */
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

    /** The GUI scale this scenario pins, or {@code null} when it takes the run's. */
    public @Nullable Integer guiScale() {
        return guiScale;
    }

    /** Whether the HUD is hidden while this scenario runs. */
    public boolean hideGui() {
        return hideGui;
    }

    /** The settle time after each step, before it is resolved against the run's. */
    public int settleMs() {
        return settleMs;
    }

    /** The wait budget for a step that repeats itself, before it is resolved against the run's. */
    public int defaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    /** The budget for the whole scenario, before it is resolved against the run's. */
    public int scenarioTimeoutMs() {
        return scenarioTimeoutMs;
    }

    /** Whether a failing step captures the frame it failed in, before the run's veto. */
    public boolean captureOnFailure() {
        return captureOnFailure;
    }

    /** The session tier this scenario needs; see {@link Session}. */
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
