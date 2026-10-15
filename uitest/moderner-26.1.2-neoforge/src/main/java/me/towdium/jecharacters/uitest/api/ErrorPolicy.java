package me.towdium.jecharacters.uitest.api;

/**
 * What a failing step does to the rest of the queue.
 *
 * <p>Constants are lowerCamelCase because this repository's style gate rejects UPPER_SNAKE enum
 * constants; {@link #fromString(String)} accepts the upper-case spelling a property would use.
 */
public enum ErrorPolicy {

    /** Record the failure, run the scenario's teardown steps and move to the next scenario. */
    abortScenario,

    /** Record the failure and keep running the remaining steps of this scenario. */
    continueScenario,

    /** Record the failure, run teardown and end the whole run. */
    abortRun;

    /** Reads either spelling; anything unrecognised means {@link #abortScenario}. */
    public static ErrorPolicy fromString(String raw) {
        String value = raw.trim();
        if ("CONTINUE_SCENARIO".equalsIgnoreCase(value)) return continueScenario;
        if ("ABORT_RUN".equalsIgnoreCase(value)) return abortRun;
        return abortScenario;
    }
}
