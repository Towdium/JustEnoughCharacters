package me.towdium.jecharacters.uitest.engine.report;

import me.towdium.jecharacters.uitest.api.CaptureRef;
import me.towdium.jecharacters.uitest.api.Status;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

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
        this.tags = Collections.unmodifiableList(new ArrayList<>(tags));
        this.session = session;
    }

    public ScenarioReport(String name, @Nullable String group, List<String> tags) {
        this(name, group, tags, "shared");
    }

    public String name() {
        return name;
    }

    public @Nullable String group() {
        return group;
    }

    public String session() {
        return session;
    }

    public List<String> tags() {
        return Collections.unmodifiableList(new ArrayList<>(tags));
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
        return Collections.unmodifiableList(new ArrayList<>(steps));
    }

    public void complete(Status result, long millis, @Nullable String note) {
        this.status = result.wire();
        this.durationMs = millis;
        this.message = note;
    }

    public StepReport addStep(StepReport step) {
        steps.add(step);
        return step;
    }

    public List<CaptureRef> captures() {
        List<CaptureRef> all = new ArrayList<>();
        for (StepReport step : steps) {
            all.addAll(step.captures());
        }
        return all;
    }
}
