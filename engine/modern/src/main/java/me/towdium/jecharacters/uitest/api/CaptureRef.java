package me.towdium.jecharacters.uitest.api;

public record CaptureRef(String kind, String path, boolean suspect) {

    public boolean matches(String written) {
        return written.equals(path);
    }

    public CaptureRef relocated(String writtenKind, String writtenPath, boolean writtenSuspect) {
        return new CaptureRef(writtenKind, writtenPath, writtenSuspect);
    }
}
