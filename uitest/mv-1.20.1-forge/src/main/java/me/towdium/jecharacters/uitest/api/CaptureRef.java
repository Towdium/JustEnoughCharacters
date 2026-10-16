package me.towdium.jecharacters.uitest.api;

/**
 * A screenshot the run produced.
 *
 * <p><b>Port note.</b> The upstream harness also carried the structural path of the cropped widget
 * for element captures. Element capture is not ported — its rectangle comes from the CloudLib widget
 * tree — so a ref is its kind, its path and the flat-colour verdict alone.
 *
 * @param kind    {@code FULL}, {@code SURFACE} or {@code SEQUENCE}
 * @param path    the file, relative to the run's output directory, with forward slashes
 * @param suspect whether the image looked like a single flat colour, which usually means the wrong
 *                framebuffer was read
 */
public record CaptureRef(String kind, String path, boolean suspect) {

    /** Whether {@code written} is the file this ref was a guess at. */
    public boolean matches(String written) {
        return written.equals(path);
    }

    /** This ref, re-pointed at the file the pipeline really wrote. */
    public CaptureRef relocated(String writtenKind, String writtenPath, boolean writtenSuspect) {
        return new CaptureRef(writtenKind, writtenPath, writtenSuspect);
    }
}
