package me.towdium.jecharacters.uitest.engine.report;

public final class ErrorInfo {

    private final String type;
    private final String message;
    private final java.util.List<String> stackTrace;

    public ErrorInfo(String type, String message, java.util.List<String> stackTrace) {
        this.type = type;
        this.message = message;
        this.stackTrace = java.util.Collections.unmodifiableList(
                new java.util.ArrayList<String>(stackTrace));
    }

    public static ErrorInfo of(Throwable thrown) {
        java.io.StringWriter writer = new java.io.StringWriter();
        thrown.printStackTrace(new java.io.PrintWriter(writer));
        java.util.List<String> frames = new java.util.ArrayList<String>();
        for (String line : writer.toString().split("\n")) {
            frames.add(stripTrailing(line));
        }
        return new ErrorInfo(
                thrown.getClass().getName(),
                thrown.getMessage() == null ? "" : thrown.getMessage(),
                frames);
    }

    public String type() {
        return type;
    }

    public String message() {
        return message;
    }

    public java.util.List<String> stackTrace() {
        return stackTrace;
    }

    private static String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) end--;
        return line.substring(0, end);
    }
}
