package me.towdium.jecharacters.uitest.engine.report;

import me.towdium.jecharacters.uitest.api.CaptureRef;
import me.towdium.jecharacters.uitest.api.CheckResult;
import me.towdium.jecharacters.uitest.api.Status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One step's outcome.
 *
 * <p><b>Port note.</b> The 1.21.1 class as a plain Java 8 class. The fields stay private because the
 * report model is serialized by reflection — Gson writes the field names this class declares, which
 * is why they must not change — and the accessors are what the writer and the tests read.
 * {@link #markWritten(String, String, boolean)} is kept although this line's capture is synchronous,
 * because the report shape it repairs (a capture that names the guessed path rather than the written
 * one) is still reachable when a capture fails and leaves the ref unrelocated.
 */
public final class StepReport {

    private final int index;
    private final String name;
    private final String kind;
    private final String role;
    private final String group;

    private String status = Status.pass.wire();
    private long durationMs;
    private int attempts;
    private int settleMs;
    private String waitingFor;
    private final List<CheckResult> checks = new ArrayList<CheckResult>();
    private final List<CaptureRef> captures = new ArrayList<CaptureRef>();
    private ErrorInfo error;
    private final List<String> log = new ArrayList<String>();

    public StepReport(int index, String name, String kind, String role, String group) {
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

    public String group() {
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

    public String waitingFor() {
        return waitingFor;
    }

    public List<CheckResult> checks() {
        return Collections.unmodifiableList(new ArrayList<CheckResult>(checks));
    }

    public List<CaptureRef> captures() {
        return Collections.unmodifiableList(new ArrayList<CaptureRef>(captures));
    }

    public ErrorInfo error() {
        return error;
    }

    public List<String> log() {
        return Collections.unmodifiableList(new ArrayList<String>(log));
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
    public void setWaitingFor(String reason) {
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
     * Re-points any capture this step recorded at the file the capture really wrote.
     *
     * <p>On this line a full-frame capture is written inside its own step, so the ref is already
     * right when the step finishes; the correction still exists because the runner records the
     * suspect verdict from the image, which only the capture side knows, and because a later wave's
     * queued captures will arrive a frame late exactly as they do on 1.21.1.
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
