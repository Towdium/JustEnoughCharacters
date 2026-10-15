package me.towdium.jecharacters.uitest.engine.report;

import me.towdium.jecharacters.uitest.api.Status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The whole run.
 *
 * <p>Written synchronously and atomically on purpose: an asynchronous write would race the process
 * exit and lose exactly the report a failure needs.
 *
 * <p><b>Port note.</b> The 1.21.1 class as a plain Java 8 class. The {@code schema} field is the same
 * {@code 1}: the JSON shape is shared across every checkout, and the 1.12.2 additions are none,
 * because every column the other reports carry has a meaning here too.
 */
public final class RunReport {

    private final int schema = 1;
    private final String runId;
    private final String selection;
    private final String startedAt;
    private final Environment environment;
    private final List<ScenarioReport> scenarios = new ArrayList<ScenarioReport>();

    private String status = Status.pass.wire();
    private String finishedAt = "";
    private long durationMs;
    private final Totals totals = new Totals();

    /** The run's restart schedule, one spelled-out line; set once the selection is known. */
    private String sessionPlan = "";

    public RunReport(String runId, String selection, Environment environment, String startedAt) {
        this.runId = runId;
        this.selection = selection;
        this.environment = environment;
        this.startedAt = startedAt;
    }

    public int schema() {
        return schema;
    }

    public String runId() {
        return runId;
    }

    public String selection() {
        return selection;
    }

    public String startedAt() {
        return startedAt;
    }

    public String finishedAt() {
        return finishedAt;
    }

    public long durationMs() {
        return durationMs;
    }

    public String status() {
        return status;
    }

    public Environment environment() {
        return environment;
    }

    public Totals totals() {
        return totals;
    }

    /** Records the session plan, before the first scenario runs. */
    public String sessionPlan() {
        return sessionPlan;
    }

    public void sessionPlan(String plan) {
        this.sessionPlan = plan;
    }

    public List<ScenarioReport> scenarios() {
        return Collections.unmodifiableList(new ArrayList<ScenarioReport>(scenarios));
    }

    public ScenarioReport addScenario(ScenarioReport scenario) {
        scenarios.add(scenario);
        return scenario;
    }

    /** Whether the whole run should fail. */
    public boolean failed() {
        return Status.fromWire(status).failed();
    }

    /** Closes the run. */
    public void complete(Status result, String finished, long millis) {
        this.status = result.wire();
        this.finishedAt = finished;
        this.durationMs = millis;
    }
}
