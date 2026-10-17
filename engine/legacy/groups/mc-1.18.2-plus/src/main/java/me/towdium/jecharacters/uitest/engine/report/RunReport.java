package me.towdium.jecharacters.uitest.engine.report;

import me.towdium.jecharacters.uitest.api.Status;

import java.util.ArrayList;
import java.util.List;

public final class RunReport {

    private final int schema = 1;

    private final String runId;

    private final String selection;

    private final String startedAt;

    private final Environment environment;

    private final List<ScenarioReport> scenarios = new ArrayList<>();

    private String status = Status.pass.wire();

    private String finishedAt = "";

    private long durationMs;

    private final Totals totals = new Totals();

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

    public String sessionPlan() {
        return sessionPlan;
    }

    public void sessionPlan(String plan) {
        this.sessionPlan = plan;
    }

    public List<ScenarioReport> scenarios() {
        return List.copyOf(scenarios);
    }

    public ScenarioReport addScenario(ScenarioReport scenario) {
        scenarios.add(scenario);
        return scenario;
    }

    public boolean failed() {
        return Status.fromWire(status).failed();
    }

    public void complete(Status result, String finished, long millis) {
        this.status = result.wire();
        this.finishedAt = finished;
        this.durationMs = millis;
    }
}
