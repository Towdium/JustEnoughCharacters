package me.towdium.jecharacters.uitest.capture;

public final class CaptureRequest {

    private final Kind kind;
    private final String scenario;
    private final String label;
    private final int stepIndex;

    public CaptureRequest(Kind kind, String scenario, String label, int stepIndex) {
        this.kind = kind;
        this.scenario = scenario;
        this.label = label;
        this.stepIndex = stepIndex;
    }

    public Kind kind() {
        return kind;
    }

    public String scenario() {
        return scenario;
    }

    public String label() {
        return label;
    }

    public int stepIndex() {
        return stepIndex;
    }

    public enum Kind {
        full
    }
}
