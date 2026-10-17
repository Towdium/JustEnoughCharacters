package me.towdium.jecharacters.uitest.engine.report;

import me.towdium.jecharacters.uitest.api.CaptureRef;
import me.towdium.jecharacters.uitest.api.CheckResult;
import me.towdium.jecharacters.uitest.api.Status;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

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
        return Collections.unmodifiableList(new ArrayList<>(checks));
    }

    public List<CaptureRef> captures() {
        return Collections.unmodifiableList(new ArrayList<>(captures));
    }

    public @Nullable ErrorInfo error() {
        return error;
    }

    public List<String> log() {
        return Collections.unmodifiableList(new ArrayList<>(log));
    }

    public void complete(Status result, long millis, int attemptCount) {
        this.status = result.wire();
        this.durationMs = millis;
        this.attempts = attemptCount;
    }

    public void setSettleMs(int millis) {
        this.settleMs = millis;
    }

    public void setWaitingFor(@Nullable String reason) {
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
