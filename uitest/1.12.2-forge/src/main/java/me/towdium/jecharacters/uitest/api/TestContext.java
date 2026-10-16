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

    public boolean repeatRequested() {
        return repeatRequested;
    }

    public String repeatReason() {
        return repeatReason;
    }

    public List<CheckResult> checks() {
        return checks;
    }

    public List<String> log() {
        return logLines;
    }

    public List<CaptureRef> captures() {
        return captures;
    }

    public boolean checksFailed() {
        for (CheckResult check : checks) {
            if (!check.passed()) return true;
        }
        return false;
    }

    // endregion

    // region the scenario's own surface

    public String scenarioName() {
        return scenarioName;
    }

    public int stepIndex() {
        return stepIndex;
    }

    public Minecraft minecraft() {
        return minecraft;
    }

    public File outDir() {
        return outDir;
    }

    public Map<String, Object> state() {
        return state;
    }

    public GuiScreen screen() {
        return minecraft.currentScreen;
    }

    public GuiScreen screenOrThrow() {
        GuiScreen screen = minecraft.currentScreen;
        if (screen == null) {
            throw new IllegalStateException("no screen is open");
        }
        return screen;
    }

    public void repeat() {
        repeat("waiting");
    }

    public void repeat(String reason) {
        repeatRequested = true;
        repeatReason = reason;
    }

    public void log(String line) {
        logLines.add(line);
        TestContext.log.info("[{}] {}", scenarioName, line);
    }

    public void check(String label, boolean passed) {
        checks.add(CheckResult.of(label, passed));
    }

    public void check(String label, boolean passed, String detail) {
        checks.add(new CheckResult(label, passed, detail));
    }

    public void checkEquals(String label, Object expected, Object actual) {
        boolean equal = expected == null ? actual == null : expected.equals(actual);
        check(label, equal, "expected " + expected + " but was " + actual);
    }

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

    public void recordWrittenCapture(String label, String path) {
        writtenCaptures.put(label, path);
    }

    public File writtenCapture(String label) {
        String relative = writtenCaptures.get(label);
        return relative == null ? null : new File(outDir, relative);
    }

    public static String screenshotPath(String scenario, String label, int stepIndex) {
        return "screenshots/" + sanitize(scenario) + "/" + String.format(Locale.ROOT, "%02d_", stepIndex)
                + sanitize(label) + ".png";
    }

    public static String sanitize(String raw) {
        return raw.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    // endregion
}
