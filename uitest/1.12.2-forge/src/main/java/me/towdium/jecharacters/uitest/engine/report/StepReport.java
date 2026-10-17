package me.towdium.jecharacters.uitest.engine.report;

import me.towdium.jecharacters.uitest.api.CaptureRef;
import me.towdium.jecharacters.uitest.api.CheckResult;
import me.towdium.jecharacters.uitest.api.Status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StepReport {

    private final int index;
    private final String name;
    private final String kind;
    private final String role;

    private String status = Status.pass.wire();
    private long durationMs;
    private int attempts;
    private int settleMs;
    private String waitingFor;
    private final List<CheckResult> checks = new ArrayList<CheckResult>();
    private final List<CaptureRef> captures = new ArrayList<CaptureRef>();
    private ErrorInfo error;
    private final List<String> log = new ArrayList<String>();

    public StepReport(int index, String name, String kind, String role) {
        this.index = index;
        this.name = name;
        this.kind = kind;
        this.role = role;
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

    public void complete(Status result, long millis, int attemptCount) {
        this.status = result.wire();
        this.durationMs = millis;
        this.attempts = attemptCount;
    }

    public void setSettleMs(int millis) {
        this.settleMs = millis;
    }

    public void setWaitingFor(String reason) {
        this.waitingFor = reason;
    }

    public void failed(Throwable thrown) {
        this.error = ErrorInfo.of(thrown);
        this.status = Status.error.wire();
    }

    public void mark(Status result) {
        this.status = result.wire();
    }

    public void addCheck(CheckResult check) {
        checks.add(check);
    }

    public void addCapture(CaptureRef capture) {
        captures.add(capture);
    }

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
