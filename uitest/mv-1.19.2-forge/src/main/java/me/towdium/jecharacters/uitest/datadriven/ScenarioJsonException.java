package me.towdium.jecharacters.uitest.datadriven;

public class ScenarioJsonException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ScenarioJsonException(String message) {
        super(message);
    }

    public ScenarioJsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
