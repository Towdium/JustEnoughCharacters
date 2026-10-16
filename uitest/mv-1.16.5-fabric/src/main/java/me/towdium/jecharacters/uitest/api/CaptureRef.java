package me.towdium.jecharacters.uitest.api;

/**
 * A screenshot the run produced.
 *
 * <p><b>Port note.</b> The upstream harness also carried the structural path of the cropped widget
 * for element captures. Element capture is not ported — its rectangle comes from the CloudLib widget
 * tree — so a ref is its kind, its path and the flat-colour verdict alone.
 *
 * <p><b>Port note (Java 8).</b> The 1.21.1 record of the same name, as a plain immutable class: Java
 * 8 has no records, so the fields are final, the accessors are the record components' names and
 * the public constructor is the canonical one. Nothing else changes.
 *
 * @param kind    {@code FULL}, {@code SURFACE} or {@code SEQUENCE}
 * @param path    the file, relative to the run's output directory, with forward slashes
 * @param suspect whether the image looked like a single flat colour, which usually means the wrong
 *                framebuffer was read
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

    public String kind() {
        return kind;
    }

    public String path() {
        return path;
    }

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
}
