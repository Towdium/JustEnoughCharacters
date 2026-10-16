package me.towdium.jecharacters.uitest.api;

import java.util.Objects;

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

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CaptureRef)) return false;
        CaptureRef that = (CaptureRef) other;
        return suspect == that.suspect
            && Objects.equals(kind, that.kind)
            && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, path, suspect);
    }

    @Override
    public String toString() {
        return "CaptureRef[kind=" + kind + ", path=" + path + ", suspect=" + suspect + "]";
    }
}
