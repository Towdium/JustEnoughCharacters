package me.towdium.jecharacters.uitest.engine.report;

import me.towdium.jecharacters.uitest.api.CaptureRef;
import me.towdium.jecharacters.uitest.api.CheckResult;
import me.towdium.jecharacters.uitest.api.Status;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One step's outcome. The fields are private because the report model is serialized by reflection;
 * the accessors are what the writer and the unit tests read.
 */
public final class StepReport {

    private final int index;

    private final String name;

    private final String kind;

    private final String role;

    private final @Nullable String group;

    private String status = Status.pass.wire();

    private long durationMs;

    private int attempts;

    private int settleMs;

    private @Nullable String waitingFor;

    private final List<CheckResult> checks = new ArrayList<>();

    private final List<CaptureRef> captures = new ArrayList<>();

    private @Nullable ErrorInfo error;

    private final List<String> log = new ArrayList<>();

    public StepReport(int index, String name, String kind, String role, @Nullable String group) {
        this.index = index;
        this.name = name;
        this.kind = kind;
        this.role = role;
        this.group = group;
    }

    public int index() {
        return index;
    }

    public String name() {
        return name;
    }

    public String kind() {
        return kind;
    }

    public String role() {
        return role;
    }

    public @Nullable String group() {
        return group;
    }

    public String status() {
        return status;
    }

    public long durationMs() {
        return durationMs;
    }

    public int attempts() {
        return attempts;
    }

    public int settleMs() {
        return settleMs;
    }

    public @Nullable String waitingFor() {
        return waitingFor;
    }

    public List<CheckResult> checks() {
        return List.copyOf(checks);
    }

    public List<CaptureRef> captures() {
        return List.copyOf(captures);
    }

    public @Nullable ErrorInfo error() {
        return error;
    }

    public List<String> log() {
        return List.copyOf(log);
    }

    /** Records the step's outcome and how long it took. */
    public void complete(Status result, long millis, int attemptCount) {
        this.status = result.wire();
        this.durationMs = millis;
        this.attempts = attemptCount;
    }

    /** Records the settle the runner applied after this step. */
    public void setSettleMs(int millis) {
        this.settleMs = millis;
    }

    /** Records why the step was still repeating when it finished. */
    public void setWaitingFor(@Nullable String reason) {
        this.waitingFor = reason;
    }

    /** Attaches the thrown failure, which also makes the status an error. */
    public void failed(Throwable thrown) {
        this.error = ErrorInfo.of(thrown);
        this.status = Status.error.wire();
    }

    /** Replaces the status without touching the error slot, for aborts and skips. */
    public void mark(Status result) {
        this.status = result.wire();
    }

    public void addCheck(CheckResult check) {
        checks.add(check);
    }

    public void addCapture(CaptureRef capture) {
        captures.add(capture);
    }

    /**
     * Re-points any capture this step recorded at the file the pipeline wrote.
     *
     * <p>The pipeline services one queued request per frame, so a capture a step queued is written
     * on a later frame — after this report was filled in. Without this the report would name the
     * path the context guessed, which is wrong for every element capture and hides the flat-colour
     * verdict.
     */
    public void markWritten(String kind, String path, boolean suspect) {
        for (int i = 0; i < captures.size(); i++) {
            CaptureRef ref = captures.get(i);
            if (ref.matches(path)) {
                captures.set(i, ref.relocated(kind, path, suspect));
            }
        }
    }

    public void addLog(String line) {
        log.add(line);
    }
}
