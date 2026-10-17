package me.towdium.jecharacters.uitest.api;

import me.towdium.jecharacters.uitest.platform.ClientPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class TestContext {

    private final Minecraft minecraft;
    private final InputDriver driver;
    private final CapturePipeline pipeline;
    private final @Nullable ServerContext server;
    private final String scenarioName;
    private final Path outDir;
    private final Map<String, Object> state = new HashMap<>();
    private final Map<String, String> writtenCaptures = new HashMap<>();
    private final List<CheckResult> checks = new ArrayList<>();
    private final List<String> log = new ArrayList<>();
    private final List<CaptureRef> captures = new ArrayList<>();

    private int stepIndex = -1;
    private long stepSequence = Long.MIN_VALUE;
    private boolean repeatRequested;
    private @Nullable String repeatReason;

    public TestContext(
        Minecraft minecraft,
        InputDriver driver,
        CapturePipeline pipeline,
        @Nullable ServerContext server,
        String scenarioName,
        Path outDir
    ) {
        this.minecraft = minecraft;
        this.driver = driver;
        this.pipeline = pipeline;
        this.server = server;
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
        log.clear();
        captures.clear();
    }

    public boolean repeatRequested() {
        return repeatRequested;
    }

    public @Nullable String repeatReason() {
        return repeatReason;
    }

    public List<CheckResult> checks() {
        return checks;
    }

    public List<String> log() {
        return log;
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

    public void markWritten(String kind, String path, boolean suspect) {
        for (int i = 0; i < captures.size(); i++) {
            CaptureRef ref = captures.get(i);
            if (ref.matches(path)) {
                captures.set(i, ref.relocated(kind, path, suspect));
            }
        }
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

    public InputDriver driver() {
        return driver;
    }

    public PlayerInput playerInput() {
        return driver.playerInput();
    }

    public CapturePipeline pipeline() {
        return pipeline;
    }

    public @Nullable ServerContext server() {
        return server;
    }

    public ServerContext serverOrThrow() {
        if (server == null) {
            throw new IllegalStateException("scenario '" + scenarioName + "' needs a loaded world");
        }
        return server;
    }

    public void recordWrittenCapture(String label, String path) {
        writtenCaptures.put(label, path);
    }

    public @Nullable Path writtenCapture(String label) {
        String relative = writtenCaptures.get(label);
        return relative == null ? null : outDir.resolve(relative);
    }

    public Path capturedImage(String label) {
        Path path = writtenCapture(label);
        if (path == null) {
            throw new IllegalStateException(
                "no capture labelled '" + label + "' has been written yet; capture it, wait a frame, then assert"
            );
        }
        return path;
    }

    public Map<String, Object> state() {
        return state;
    }

    public @Nullable Screen screen() {
        return (Screen) ClientPlatform.current().screen();
    }

    public Screen screenOrThrow() {
        Screen screen = (Screen) ClientPlatform.current().screen();
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
        log.add(line);
    }

    public void check(String label, boolean passed) {
        checks.add(CheckResult.of(label, passed));
    }

    public void check(String label, boolean passed, @Nullable String detail) {
        checks.add(new CheckResult(label, passed, detail));
    }

    public void checkEquals(String label, @Nullable Object expected, @Nullable Object actual) {
        boolean equal = Objects.equals(expected, actual);
        check(label, equal, "expected " + expected + " but was " + actual);
    }

    public void screenshot(String label) {
        captures.add(new CaptureRef("FULL", screenshotPath(scenarioName, label, stepIndex), false));
        pipeline.queueFull(scenarioName, label, stepIndex);
    }

    public static String screenshotPath(String scenario, String label, int stepIndex) {
        return "screenshots/" + sanitize(scenario) + "/" + String.format(Locale.ROOT, "%02d_", stepIndex)
                + sanitize(label) + ".png";
    }

    public static String sanitize(String raw) {
        return raw.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    public long gameTime() {
        var level = minecraft.level;
        return level == null ? -1L : level.getGameTime();
    }

    public boolean clientChunkLoaded(int chunkX, int chunkZ) {
        var level = minecraft.level;
        return level != null && level.hasChunk(chunkX, chunkZ);
    }

    // endregion
}
