package me.towdium.jecharacters.uitest.api;

import me.towdium.jecharacters.uitest.capture.ScreenshotCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a step body is handed: the game, the output directory, and the bookkeeping the runner reads
 * back when the step finishes.
 *
 * <p>Assertions never throw. They record a {@link CheckResult}, which is what lets one step report
 * several observations and still fail cleanly. A wait, by contrast, is expressed by calling
 * {@link #repeat(String)}: the runner then calls the same body again next frame.
 *
 * <p><b>Port note.</b> This is the 1.12.2 subset of the 1.21.1 context. Three things are gone
 * because their machinery is not on this line: the {@code InputDriver}/{@code PlayerInput} surface
 * (this line's input is the coremod's seam, and the builder calls it directly), the
 * {@code ServerContext} (there is no per-step server thread here — the integrated server ticks with
 * the client), and the {@code CapturePipeline}/{@code Timeline} (a capture is synchronous, see
 * {@link ScreenshotCapture}). What is left is the part every step body actually reads: the game, the
 * check/log/capture records, the screen, the scenario state map, and the repeat protocol.
 */
public final class TestContext {

    private static final Logger log = LogManager.getLogger("jechtest-uitest");

    private final Minecraft minecraft;
    private final String scenarioName;
    private final File outDir;
    private final Map<String, Object> state = new HashMap<String, Object>();
    private final Map<String, String> writtenCaptures = new HashMap<String, String>();
    private final Map<String, Boolean> suspectCaptures = new HashMap<String, Boolean>();
    private final List<CheckResult> checks = new ArrayList<CheckResult>();
    private final List<String> logLines = new ArrayList<String>();
    private final List<CaptureRef> captures = new ArrayList<CaptureRef>();

    private int stepIndex = -1;
    private long stepSequence = Long.MIN_VALUE;
    private boolean repeatRequested;
    private String repeatReason;

    public TestContext(Minecraft minecraft, String scenarioName, File outDir) {
        this.minecraft = minecraft;
        this.scenarioName = scenarioName;
        this.outDir = outDir;
    }

    // region runner-facing bookkeeping

    /**
     * Marks the start of a step attempt.
     *
     * <p>The repeat flag is cleared on <em>every</em> attempt, because it describes this attempt
     * only: a step that waited on an earlier frame and is satisfied on this one must be able to
     * finish. Recording, by contrast, is cleared only when a different step starts, so a step that
     * repeats keeps the checks, log lines and captures from its earlier attempts.
     */
    public void beginStep(int index, long sequence) {
        repeatRequested = false;
        repeatReason = null;
        if (sequence == stepSequence) return;
        stepSequence = sequence;
        stepIndex = index;
        checks.clear();
        logLines.clear();
        captures.clear();
    }

    /** Whether the step asked to run again next frame. */
    public boolean repeatRequested() {
        return repeatRequested;
    }

    /** Why the step is repeating, for the report's {@code waitingFor} field. */
    public String repeatReason() {
        return repeatReason;
    }

    /** The assertions recorded by this step. */
    public List<CheckResult> checks() {
        return checks;
    }

    /** The log lines recorded by this step. */
    public List<String> log() {
        return logLines;
    }

    /** The screenshots this step took. */
    public List<CaptureRef> captures() {
        return captures;
    }

    /** Whether any recorded assertion failed. */
    public boolean checksFailed() {
        for (CheckResult check : checks) {
            if (!check.passed()) return true;
        }
        return false;
    }

    // endregion

    // region the scenario's own surface

    /** The scenario this context belongs to. */
    public String scenarioName() {
        return scenarioName;
    }

    /** The index of the running step. */
    public int stepIndex() {
        return stepIndex;
    }

    /** The game. */
    public Minecraft minecraft() {
        return minecraft;
    }

    /** The run's output directory: where the reports and the captures land. */
    public File outDir() {
        return outDir;
    }

    /** Per-scenario state a step can hand to a later step. */
    public Map<String, Object> state() {
        return state;
    }

    /** The screen the game currently shows, or {@code null}. */
    public GuiScreen screen() {
        return minecraft.currentScreen;
    }

    /** The screen the game currently shows. */
    public GuiScreen screenOrThrow() {
        GuiScreen screen = minecraft.currentScreen;
        if (screen == null) {
            throw new IllegalStateException("no screen is open");
        }
        return screen;
    }

    /** Stops the step clock and runs the same step again next frame, without settling in between. */
    public void repeat() {
        repeat("waiting");
    }

    /** Stops the step clock and runs the same step again next frame, recording why. */
    public void repeat(String reason) {
        repeatRequested = true;
        repeatReason = reason;
    }

    /** Appends a line to this step's log, and to the client log so a failure is diagnosable from the tail. */
    public void log(String line) {
        logLines.add(line);
        TestContext.log.info("[{}] {}", scenarioName, line);
    }

    /** Records an assertion outcome. */
    public void check(String label, boolean passed) {
        checks.add(CheckResult.of(label, passed));
    }

    /** Records an assertion outcome with the observed value. */
    public void check(String label, boolean passed, String detail) {
        checks.add(new CheckResult(label, passed, detail));
    }

    /** Records an equality assertion. */
    public void checkEquals(String label, Object expected, Object actual) {
        boolean equal = expected == null ? actual == null : expected.equals(actual);
        check(label, equal, "expected " + expected + " but was " + actual);
    }

    /**
     * Takes a whole-framebuffer screenshot and records where it landed.
     *
     * <p>Synchronous on this line, so the ref is final the moment this returns. A capture the game's
     * own writer refused records a failed check instead of throwing: "the screenshot is missing" is
     * the observation, and the step that asked for it should still report its other checks.
     */
    public void screenshot(String label) {
        String relative = screenshotPath(scenarioName, label, stepIndex);
        try {
            CaptureRef written = ScreenshotCapture.take(outDir, relative);
            captures.add(written);
            writtenCaptures.put(label, written.path());
            suspectCaptures.put(label, Boolean.valueOf(written.suspect()));
            check("capture '" + label + "' was written", true, written.path());
        } catch (IOException | RuntimeException e) {
            captures.add(new CaptureRef(ScreenshotCapture.kind, relative, false));
            check("capture '" + label + "' was written", false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Asserts a labelled capture is on disk and is not a single flat colour.
     *
     * <p>The flat-colour verdict is a real readback property, not a marker: an all-black or all-white
     * frame means the screenshot read the wrong surface, which is exactly the failure a "the capture
     * exists" assertion would miss. It is remembered per label because the captures list is per step
     * and the screenshot was taken by an earlier step.
     */
    public void checkCaptured(String label) {
        String relative = writtenCaptures.get(label);
        if (relative == null) {
            check("capture '" + label + "' is on disk", false, "no capture has been taken under that label");
            return;
        }
        File file = new File(outDir, relative);
        boolean exists = file.isFile() && file.length() > 0L;
        check(
                "capture '" + label + "' is on disk",
                exists,
                exists ? relative + " (" + file.length() + " bytes)" : "missing " + file.getAbsolutePath());
        if (!exists) return;
        boolean suspect = Boolean.TRUE.equals(suspectCaptures.get(label));
        check(
                "capture '" + label + "' is not a flat colour",
                !suspect,
                suspect ? "the image is one flat colour - wrong framebuffer?" : null);
    }

    /** Records that a capture with the given label was written, so a later step can find it. */
    public void recordWrittenCapture(String label, String path) {
        writtenCaptures.put(label, path);
    }

    /** The capture written with the given label in this scenario, or {@code null} when there is none. */
    public File writtenCapture(String label) {
        String relative = writtenCaptures.get(label);
        return relative == null ? null : new File(outDir, relative);
    }

    /**
     * Where a capture lands: {@code screenshots/<scenario>/<NN>_<label>.png}, relative to the run's
     * output directory. Both halves must agree on this layout.
     */
    public static String screenshotPath(String scenario, String label, int stepIndex) {
        return "screenshots/" + sanitize(scenario) + "/" + String.format(Locale.ROOT, "%02d_", stepIndex)
                + sanitize(label) + ".png";
    }

    /** The pipeline's file-name rule: everything outside {@code [A-Za-z0-9._-]} becomes an underscore. */
    public static String sanitize(String raw) {
        return raw.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    // endregion
}
