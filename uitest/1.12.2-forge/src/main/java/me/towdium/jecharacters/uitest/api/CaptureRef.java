package me.towdium.jecharacters.uitest.api;

public final class CaptureRef {

    private final String kind;
    private final String path;
    private final boolean suspect;

    public CaptureRef(String kind, String path, boolean suspect) {
        this.kind = kind;
        this.path = path;
        this.suspect = suspect;
    }

    public String kind() {
        return kind;
    }

    public String path() {
        return path;
    }

    public boolean suspect() {
        return suspect;
    }

    public boolean matches(String written) {
        return written.equals(path);
    }

    public CaptureRef relocated(String writtenKind, String writtenPath, boolean writtenSuspect) {
        return new CaptureRef(writtenKind, writtenPath, writtenSuspect);
    }
}
