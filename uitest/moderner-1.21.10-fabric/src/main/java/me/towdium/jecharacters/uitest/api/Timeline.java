package me.towdium.jecharacters.uitest.api;

/**
 * A per-frame record log a scenario appends to: a name and a string blob — a dump line, a geometry
 * reading, anything a run wants to keep next to its report.
 *
 * <p>The implementation stamps the record with the frame it was appended in and the wall-clock
 * timecode, and writes it as one JSON line per record, so the log can be tailed while a run is still
 * going and a crashed run still leaves everything appended so far.
 *
 * <p>A scenario gets its timeline from {@link TestContext#timeline()} — the capture side may write
 * none, in which case appending is a no-op and the run is otherwise unaffected.
 */
public interface Timeline {

    /** Appends one record under {@code name}. */
    void record(String name, String blob);
}
