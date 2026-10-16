package me.towdium.jecharacters.uitest.datadriven;

/**
 * A scenario's step list is wrong: an unknown step name, a missing parameter, a parameter of the
 * wrong type, or a parameter no step knows.
 *
 * <p>Thrown while a scenario is being defined, which the runner already turns into a reported
 * ERROR naming the scenario — so the message carries the file, the step and the reason, and there is
 * nothing left to look up.
 */
public class ScenarioJsonException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ScenarioJsonException(String message) {
        super(message);
    }

    public ScenarioJsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
