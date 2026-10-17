package me.towdium.jecharacters.uitest.engine.report;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public record ErrorInfo(String type, String message, List<String> stackTrace) {

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
            List.copyOf(frames)
        );
    }
}
