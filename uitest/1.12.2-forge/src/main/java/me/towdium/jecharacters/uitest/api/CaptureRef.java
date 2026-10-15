package me.towdium.jecharacters.uitest.api;

/**
 * A screenshot the run produced.
 *
 * <p><b>Port note.</b> The 1.21.1 record as a plain immutable class. Only {@code FULL} captures
 * exist on this line: the upstream harness's element captures (cropped widget rectangles) and its
 * sequence/surface captures all resolve against machinery this checkout does not carry, so a ref is
 * its kind, its path and the flat-colour verdict alone — exactly the reduction the 1.21.1 class's own
 * Port note already had to make.
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

    /** {@code FULL} — a whole-framebuffer readback. */
    public String kind() {
        return kind;
    }

    /** The file, relative to the run's output directory, with forward slashes. */
    public String path() {
        return path;
    }

    /** Whether the image looked like a single flat colour, which usually means the wrong framebuffer. */
    public boolean suspect() {
        return suspect;
    }

    /** Whether {@code written} is the file this ref was a guess at. */
    public boolean matches(String written) {
        return written.equals(path);
    }

    /** This ref, re-pointed at the file the capture really wrote. */
    public CaptureRef relocated(String writtenKind, String writtenPath, boolean writtenSuspect) {
        return new CaptureRef(writtenKind, writtenPath, writtenSuspect);
    }
}
