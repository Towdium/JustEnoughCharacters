package me.towdium.jecharacters.uitest.engine.report;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * A thrown failure, flattened for the report.
 *
 * @param type       the throwable's class name
 * @param message    its message, or the empty string
 * @param stackTrace one entry per stack frame
 */
public final class ErrorInfo {

    private final String type;
    private final String message;
    private final List<String> stackTrace;

    public ErrorInfo(String type, String message, List<String> stackTrace) {
        this.type = type;
        this.message = message;
        this.stackTrace = stackTrace;
    }

    public String type() {
        return type;
    }

    public String message() {
        return message;
    }

    public List<String> stackTrace() {
        return stackTrace;
    }


    /** Flattens a throwable, keeping its whole chain's text in the stack trace. */
    public static ErrorInfo of(Throwable thrown) {
        StringWriter writer = new StringWriter();
        thrown.printStackTrace(new PrintWriter(writer));
        List<String> frames = new ArrayList<>();
        for (String line : writer.toString().split("\n")) {
            frames.add(line.stripTrailing());
        }
        return new ErrorInfo(
            thrown.getClass().getName(),
            thrown.getMessage() == null ? "" : thrown.getMessage(),
            Collections.unmodifiableList(new ArrayList<>(frames))
        );
    }
}

