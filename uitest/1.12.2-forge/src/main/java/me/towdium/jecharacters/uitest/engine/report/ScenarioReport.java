package me.towdium.jecharacters.uitest.engine.report;

import me.towdium.jecharacters.uitest.api.CaptureRef;
import me.towdium.jecharacters.uitest.api.Status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One scenario's outcome and the steps that produced it.
 *
 * <p><b>Port note.</b> The 1.21.1 class as a plain Java 8 class. The three-argument convenience
 * constructor survives with the same {@code "shared"} default, because on this line every scenario
 * really does run in the one world the run created — the session tiers are a later wave's, so
 * {@code shared} is not a placeholder here, it is the truth the run reports.
 */
public final class ScenarioReport {

    private final String name;
    private final String group;
    private final List<String> tags;
    private final String session;

    private String status = Status.skipped.wire();
    private long durationMs;
    private String message;
    private final List<StepReport> steps = new ArrayList<StepReport>();

    public ScenarioReport(String name, String group, List<String> tags, String session) {
        this.name = name;
        this.group = group;
        this.tags = Collections.unmodifiableList(new ArrayList<String>(tags));
        this.session = session;
    }

    /** A scenario whose tier is not meaningful to the caller; it defaults to {@code shared}. */
    public ScenarioReport(String name, String group, List<String> tags) {
        this(name, group, tags, "shared");
    }

    public String name() {
        return name;
    }

    public String group() {
        return group;
    }

    /** The client-session tier this scenario declared. */
    public String session() {
        return session;
    }

    public List<String> tags() {
        return Collections.unmodifiableList(new ArrayList<String>(tags));
    }

    public String status() {
        return status;
    }

    public long durationMs() {
        return durationMs;
    }

    public String message() {
        return message;
    }

    public List<StepReport> steps() {
        return Collections.unmodifiableList(new ArrayList<StepReport>(steps));
    }

    /** Records the scenario's outcome. */
    public void complete(Status result, long millis, String note) {
        this.status = result.wire();
        this.durationMs = millis;
        this.message = note;
    }

    public StepReport addStep(StepReport step) {
        steps.add(step);
        return step;
    }

    /** Every capture the scenario produced, gathered from its steps. */
    public List<CaptureRef> captures() {
        List<CaptureRef> all = new ArrayList<CaptureRef>();
        for (StepReport step : steps) {
            all.addAll(step.captures());
        }
        return all;
    }
}
