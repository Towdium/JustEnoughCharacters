package me.towdium.jecharacters.uitest.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * What a step body is handed: the game, the driver, the capture pipeline, the server context, and
 * the bookkeeping the runner reads back when the step finishes.
 *
 * <p>Assertions never throw. They record a {@link CheckResult}, which is what lets one step report
 * several observations and still fail cleanly. A wait, by contrast, is expressed by calling
 * {@link #repeat(String)}: the runner then calls the same body again next frame.
 *
 * <p><b>Port note.</b> The CloudLib-scene surface of the upstream context — {@code scene()},
 * {@code resolve}, {@code single}, {@code clickable}, the tree-snapshot and golden-image accessors
 * and the element capture — is not here: it all resolves against the CloudLib {@code Scene}/
 * {@code Widget} tree, which this harness does not carry.
 */
public final class TestContext {

    private final Minecraft minecraft;
    private final InputDriver driver;
    private final CapturePipeline pipeline;
    private final @Nullable ServerContext server;
    private final @Nullable Timeline timeline;
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
        this.timeline = pipeline.timeline(scenarioName);
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
        log.clear();
        captures.clear();
    }

    /** Whether the step asked to run again next frame. */
    public boolean repeatRequested() {
        return repeatRequested;
    }

    /** Why the step is repeating, for the report's {@code waitingFor} field. */
    public @Nullable String repeatReason() {
        return repeatReason;
    }

    /** The assertions recorded by this step. */
    public List<CheckResult> checks() {
        return checks;
    }

    /** The log lines recorded by this step. */
    public List<String> log() {
        return log;
    }

    /** The screenshots this step queued. */
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

    /**
     * Adopts the path the pipeline actually wrote, once it has written it, and records whether the
     * image looked like a single flat colour.
     *
     * <p>The kind is taken from the capture side rather than kept from the guess: which queue a
     * request went through is what the report should name, and only the sink sees that.
     */
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

    /** The input driver the run installed. */
    public InputDriver driver() {
        return driver;
    }

    /**
     * The in-world player input surface: the camera, the movement binds and the crosshair's buttons.
     *
     * <p>Prefer the {@link ScenarioBuilder} steps over calling it directly — they are the same calls
     * with the frame discipline and the teardown built in — but a step that needs a per-frame sample
     * between two gestures reads the surface from here.
     */
    public PlayerInput playerInput() {
        return driver.playerInput();
    }

    /** The capture pipeline the run installed. */
    public CapturePipeline pipeline() {
        return pipeline;
    }

    /** The server context, or {@code null} when the run started without a world. */
    public @Nullable ServerContext server() {
        return server;
    }

    /** The server context, for steps that need a world. */
    public ServerContext serverOrThrow() {
        if (server == null) {
            throw new IllegalStateException("scenario '" + scenarioName + "' needs a loaded world");
        }
        return server;
    }

    /** The per-scenario timeline log, or {@code null} when the capture side writes none. */
    public @Nullable Timeline timeline() {
        return timeline;
    }

    /** Records that the pipeline wrote a capture with the given label, so a later step can find it. */
    public void recordWrittenCapture(String label, String path) {
        writtenCaptures.put(label, path);
    }

    /**
     * The capture written with the given label in this scenario, or {@code null} when nothing has
     * been written under that label yet.
     *
     * <p>The pipeline services one queued request per frame, so a capture a step queued is on disk
     * only from the next frame on.
     */
    public @Nullable Path writtenCapture(String label) {
        String relative = writtenCaptures.get(label);
        return relative == null ? null : outDir.resolve(relative);
    }

    /**
     * The absolute path of the capture written with the given label in this scenario.
     *
     * @throws IllegalStateException when no capture with that label has been written yet, which
     *                               usually means the assertion runs in the same frame as the
     *                               screenshot that feeds it
     */
    public Path capturedImage(String label) {
        Path path = writtenCapture(label);
        if (path == null) {
            throw new IllegalStateException(
                "no capture labelled '" + label + "' has been written yet; capture it, wait a frame, then assert"
            );
        }
        return path;
    }

    /** Per-scenario state a step can hand to a later step. */
    public Map<String, Object> state() {
        return state;
    }

    /** The screen the game currently shows, or {@code null}. */
    public @Nullable Screen screen() {
        return minecraft.screen;
    }

    /** The screen the game currently shows. */
    public Screen screenOrThrow() {
        Screen screen = minecraft.screen;
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

    /** Appends a line to this step's log. */
    public void log(String line) {
        log.add(line);
    }

    /** Records an assertion outcome. */
    public void check(String label, boolean passed) {
        checks.add(CheckResult.of(label, passed));
    }

    /** Records an assertion outcome with the observed value. */
    public void check(String label, boolean passed, @Nullable String detail) {
        checks.add(new CheckResult(label, passed, detail));
    }

    /** Records an equality assertion. */
    public void checkEquals(String label, @Nullable Object expected, @Nullable Object actual) {
        boolean equal = Objects.equals(expected, actual);
        check(label, equal, "expected " + expected + " but was " + actual);
    }

    /** Queues a full-frame screenshot and records where it will land. */
    public void screenshot(String label) {
        captures.add(new CaptureRef("FULL", screenshotPath(scenarioName, label, stepIndex), false));
        pipeline.queueFull(scenarioName, label, stepIndex);
    }

    /**
     * Queues a readback of an arbitrary colour surface — a Minecraft render target — and records
     * where it will land.
     *
     * <p>The supplier is resolved by the pipeline on the frame the capture is serviced, so it names
     * the current frame's surface rather than the frame this call ran in.
     */
    public void screenshotSurface(String label, Supplier<?> surface) {
        captures.add(new CaptureRef("SURFACE", screenshotPath(scenarioName, label, stepIndex), false));
        pipeline.queueSurface(scenarioName, label, stepIndex, surface);
    }

    /**
     * Queues a labelled run of {@code frames} full-frame captures, one per frame.
     *
     * <p>The pipeline services one request per frame, so these are consecutive frames: the run drains
     * over the following {@code frames} frames, which is why a caller waits for them before asserting.
     */
    public void screenshotSequence(String label, int frames) {
        if (frames <= 0) return;
        for (int frame = 0; frame < frames; frame++) {
            captures.add(new CaptureRef("SEQUENCE", sequencePath(scenarioName, label, frame), false));
        }
        pipeline.queueSequence(scenarioName, label, frames, stepIndex);
    }

    /** Appends one record to the scenario's timeline, when the capture side writes one. */
    public void recordTimeline(String name, String blob) {
        Timeline log = timeline;
        if (log == null) return;
        log.record(name, blob);
        log("timeline " + name + ": " + blob);
    }

    /**
     * Where a capture lands: {@code screenshots/<scenario>/<NN>_<label>.png}, relative to the run's
     * output directory. Both halves must agree on this layout.
     */
    public static String screenshotPath(String scenario, String label, int stepIndex) {
        return "screenshots/" + sanitize(scenario) + "/" + String.format(Locale.ROOT, "%02d_", stepIndex)
                + sanitize(label) + ".png";
    }

    /**
     * The label one frame of a sequence carries: {@code <label>_%04d}.
     *
     * <p>This is the api's copy of the capture side's rule — a sequence frame has no step prefix, so
     * the recorded ref and the written file have to agree on the name alone. The other half is
     * {@code FrameSequenceCapture.frameLabel}.
     */
    public static String sequenceFrameLabel(String label, int frame) {
        return sanitize(label) + "_" + String.format(Locale.ROOT, "%04d", frame);
    }

    /** Where one frame of a sequence lands: {@code screenshots/<scenario>/<label>_%04d.png}. */
    public static String sequencePath(String scenario, String label, int frame) {
        return "screenshots/" + sanitize(scenario) + "/" + sequenceFrameLabel(label, frame) + ".png";
    }

    /** The pipeline's file-name rule: everything outside {@code [A-Za-z0-9._-]} becomes an underscore. */
    public static String sanitize(String raw) {
        return raw.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    /** The client level's game time, or {@code -1} when no level is loaded. */
    public long gameTime() {
        var level = minecraft.level;
        return level == null ? -1L : level.getGameTime();
    }

    /** Whether the client has a chunk loaded at the given column. */
    public boolean clientChunkLoaded(int chunkX, int chunkZ) {
        var level = minecraft.level;
        return level != null && level.hasChunk(chunkX, chunkZ);
    }

    /** Whether the client has a block entity at the given position. */
    public boolean clientBlockEntityLoaded(int x, int y, int z) {
        var level = minecraft.level;
        return level != null && level.getBlockEntity(new BlockPos(x, y, z)) != null;
    }

    // endregion
}
