package me.towdium.jecharacters.uitest.api;

public enum ErrorPolicy {

    abortScenario,

    continueScenario,

    abortRun;

    public static ErrorPolicy fromString(String raw) {
        String value = raw.trim();
        if ("CONTINUE_SCENARIO".equalsIgnoreCase(value)) return continueScenario;
        if ("ABORT_RUN".equalsIgnoreCase(value)) return abortRun;
        return abortScenario;
    }
}
