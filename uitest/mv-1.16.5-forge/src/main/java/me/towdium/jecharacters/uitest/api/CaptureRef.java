package me.towdium.jecharacters.uitest.api;

import java.util.Objects;

/**
 * A screenshot the run produced.
 *
 * <p><b>Port note.</b> The upstream harness also carried the structural path of the cropped widget
 * for element captures. Element capture is not ported — its rectangle comes from the CloudLib widget
 * tree — so a ref is its kind, its path and the flat-colour verdict alone.
 *
 * <p><b>Port note.</b> The 1.21.1 record as a plain immutable class: Java 8 has no records, so the
 * three components are private final fields, the constructor is the canonical one, and the accessors
 * keep the component names. {@code equals}, {@code hashCode} and {@code toString} are spelled out
 * component for component, because a ref is a value. The record's {@code @param} javadoc moved onto
 * the accessors, which is where a plain class can carry it.
 */
public final class CaptureRef {

    private final String kind;

    private final String path;

    private final boolean suspect;

    public CaptureRef(String kind, String path, boolean suspect) {
        this.kind = kind;
        this.path = path;
        this.suspect = suspect;
    }

    /** {@code FULL}, {@code SURFACE} or {@code SEQUENCE}. */
    public String kind() {
        return kind;
    }

    /** The file, relative to the run's output directory, with forward slashes. */
    public String path() {
        return path;
    }

    /**
     * Whether the image looked like a single flat colour, which usually means the wrong framebuffer
     * was read.
     */
    public boolean suspect() {
        return suspect;
    }

    /** Whether {@code written} is the file this ref was a guess at. */
    public boolean matches(String written) {
        return written.equals(path);
    }

    /** This ref, re-pointed at the file the pipeline really wrote. */
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
