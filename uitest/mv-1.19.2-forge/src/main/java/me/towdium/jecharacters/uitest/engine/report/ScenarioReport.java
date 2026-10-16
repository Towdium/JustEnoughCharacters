package me.towdium.jecharacters.uitest.engine.report;

import me.towdium.jecharacters.uitest.api.CaptureRef;
import me.towdium.jecharacters.uitest.api.Status;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** One scenario's outcome and the steps that produced it. */
public final class ScenarioReport {

    private final String name;

    private final @Nullable String group;

    private final List<String> tags;

    private final String session;

    private String status = Status.skipped.wire();

    private long durationMs;

    private @Nullable String message;

    private final List<StepReport> steps = new ArrayList<>();

    public ScenarioReport(String name, @Nullable String group, List<String> tags, String session) {
        this.name = name;
        this.group = group;
        this.tags = List.copyOf(tags);
        this.session = session;
    }

    /**
     * A scenario whose tier is not meaningful to the caller — an MP segment report, or a report a test
     * builds by hand. The tier a client scenario declared comes through the four-argument form.
     */
    public ScenarioReport(String name, @Nullable String group, List<String> tags) {
        this(name, group, tags, "shared");
    }

    public String name() {
        return name;
    }

    public @Nullable String group() {
        return group;
    }

    /** The client-session tier this scenario declared: {@code shared}, {@code reenter} or {@code fresh}. */
    public String session() {
        return session;
    }

    public List<String> tags() {
        return List.copyOf(tags);
    }

    public String status() {
        return status;
    }

    public long durationMs() {
        return durationMs;
    }

    public @Nullable String message() {
        return message;
    }

    public List<StepReport> steps() {
        return List.copyOf(steps);
    }

    /** Records the scenario's outcome. */
    public void complete(Status result, long millis, @Nullable String note) {
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
        List<CaptureRef> all = new ArrayList<>();
        for (StepReport step : steps) {
            all.addAll(step.captures());
        }
        return all;
    }
}
