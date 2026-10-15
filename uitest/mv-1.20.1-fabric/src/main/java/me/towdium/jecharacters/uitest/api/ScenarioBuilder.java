package me.towdium.jecharacters.uitest.api;

import me.towdium.jecharacters.uitest.match.MatchFilter;
import me.towdium.jecharacters.uitest.match.MatchRecorder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Builds one scenario's script.
 *
 * <p>A step is one attempt per frame. Gestures are therefore written out as the frames they really
 * take: a click is a hover, a press and a release, and a drag is eight frames, because a drag only
 * begins once the source sees the pointer leave it while a button is held.
 *
 * <p>Steps that reach the integrated server submit their work on the first attempt and repeat the
 * step until the server has run it, which is what keeps the client's frame clock and the server's
 * tick loop in step with each other.
 *
 * <p>A step that repeats must be able to run its first attempt again after a timeout, so every
 * counter a wait uses lives in the step body's own closure rather than in a field here.
 *
 * <h2>Port note: the coordinate gestures</h2>
 *
 * <p>The upstream CloudLib harness addressed every screen gesture through a {@link Object selector}
 * resolved against the CloudLib {@code Scene}/{@code Widget} tree. This harness does not carry that
 * tree — the widget-model API, its selectors and its tree assertions are a separate port — so the
 * gesture verbs here take <em>GUI-scaled coordinates</em> instead of a selector. The verbs, the
 * step shapes and the frame discipline are unchanged; only the way a target is named is. The
 * screen-space driver seam underneath them ({@code placeCursor}/{@code moveTo}/{@code mouseDown}/
 * {@code mouseUp}/{@code dragTo}/{@code scroll}/{@code keyDown}/{@code keyUp}/{@code charTyped}) is
 * the upstream one verbatim.
 */
public final class ScenarioBuilder {

    private static final int teardownIndexBase = 1_000;

    private static final int serverTeardownIndexBase = 2_000;

    private static final int serverStepTimeoutMs = 60_000;

    private static final String dragLastX = "jech.uitest.drag.lastX";

    private static final String dragLastY = "jech.uitest.drag.lastY";

    private static final int doubleClickGapMs = 30;

    /** How long a match assertion waits for a record before it fails, when the caller names none. */
    private static final int defaultMatchWindowMs = 3_000;

    /** How much longer than its own window a match assertion's step budget is, for the deciding frame. */
    private static final int matchWindowSlackMs = 5_000;

    private final List<Step> steps = new ArrayList<>();

    private final List<Step> teardownSteps = new ArrayList<>();

    private final List<Step> serverTeardownSteps = new ArrayList<>();

    private final Deque<String> groups = new ArrayDeque<>();

    private ScenarioOptions options = ScenarioOptions.defaults();

    private final Deque<List<Step>> targets = new ArrayDeque<>(List.of(steps));

    private int pendingSettleMs;

    private int pendingTimeoutMs;

    private boolean embodied;

    // region structure

    /** The scenario's steps, in order. */
    public List<Step> steps() {
        return List.copyOf(steps);
    }

    /** Steps that run on the client after the scenario's steps, even when it aborted. */
    public List<Step> teardownSteps() {
        return List.copyOf(teardownSteps);
    }

    /** Steps that run on the integrated server after the scenario's steps. */
    public List<Step> serverTeardownSteps() {
        return List.copyOf(serverTeardownSteps);
    }

    /** The per-scenario tuning this scenario resolved. */
    public ScenarioOptions options() {
        return options;
    }

    /** Overrides the GUI scale this scenario is pinned to. */
    public ScenarioBuilder guiScale(int scale) {
        options = options.withGuiScale(scale);
        return this;
    }

    /** Overrides whether the in-game HUD is hidden while this scenario runs. */
    public ScenarioBuilder hideGui(boolean hide) {
        options = options.withHideGui(hide);
        return this;
    }

    /** The settle time applied to every step declared after this call; {@code 0} means the default. */
    public ScenarioBuilder settleMs(int ms) {
        pendingSettleMs = ms;
        return this;
    }

    /** The wait budget applied to every step declared after this call; {@code 0} means the default. */
    public ScenarioBuilder timeoutMs(int ms) {
        pendingTimeoutMs = ms;
        return this;
    }

    /** Labels the steps the body declares. Nested groups are joined with a slash. */
    public ScenarioBuilder group(String name, Runnable body) {
        groups.addLast(name);
        try {
            body.run();
        } finally {
            groups.removeLast();
        }
        return this;
    }

    /** Declares the body's steps a number of times over. */
    public ScenarioBuilder repeat(int times, Runnable body) {
        for (int i = 0; i < times; i++) {
            group("repeat " + (i + 1), body);
        }
        return this;
    }

    /**
     * Registers client cleanup that runs after the scenario's steps, aborted or not.
     *
     * <p>The body is deferred: it runs once, at teardown time, as a single step on the client
     * thread. Write plain statements here — close the screen, stop a helper — never builder
     * calls: a builder call inside a deferred body registers a step that never runs, and the
     * wrapper turns that mistake into an error instead of a silent no-op.
     */
    public ScenarioBuilder teardown(Runnable body) {
        addTo(teardownSteps, StepKind.teardown, "teardown", context -> {
            int registered = steps.size() + teardownSteps.size() + serverTeardownSteps.size();
            body.run();
            if (steps.size() + teardownSteps.size() + serverTeardownSteps.size() != registered) {
                throw new IllegalStateException(
                    "a teardown body runs at teardown time, so it must be plain statements; "
                            + "a builder call in it registers a step that never runs"
                );
            }
        });
        return this;
    }

    /**
     * Registers server cleanup that runs after the scenario's steps, aborted or not.
     *
     * <p>Unlike {@link #teardown}, the body is a DSL body evaluated at define time: the world
     * steps it declares ({@code clearArea}, {@code setBlock}, …) become the server teardown
     * steps, because server state can only change through their submit-and-repeat handshake —
     * plain statements would run on the client thread and touch nothing.
     */
    public ScenarioBuilder teardownServer(Runnable body) {
        return inList(serverTeardownSteps, "teardownServer", body);
    }

    /**
     * Declares one step of a body the builder does not have a helper for.
     *
     * <p>It exists for an engine extension whose steps mean nothing to a plain scenario (an
     * announce, a barrier, a probe round trip): those still have to be steps, so that they get the
     * settling, the wait budget and the report entry every other step gets.
     */
    public ScenarioBuilder step(StepKind kind, String name, StepBody body) {
        add(kind, name, body);
        return this;
    }

    /** Writes a line into the step log. */
    public ScenarioBuilder log(String line) {
        add(StepKind.log, "log", context -> context.log(line));
        return this;
    }

    /**
     * Runs a client-side action that is neither a wait nor an assertion.
     *
     * <p>It exists for the state a scenario has to change between assertions — flipping a label the
     * next step waits for, or invoking a screen's own close hook — which is otherwise expressed as
     * a side effect hidden inside a {@code check}. An action step takes one frame and reports no
     * checks.
     */
    public ScenarioBuilder action(String name, Consumer<TestContext> body) {
        add(StepKind.action, "action " + name, body::accept);
        return this;
    }

    // endregion

    // region world and server

    /** Places one block. */
    public ScenarioBuilder setBlock(int x, int y, int z, BlockState state) {
        addServer(
            StepKind.setBlock,
            "setBlock " + x + "," + y + "," + z,
            context -> context.serverOrThrow().setBlock(context, new BlockPos(x, y, z), state)
        );
        return this;
    }

    /** Fills an inclusive box with one block. */
    public ScenarioBuilder fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        addServer(
            StepKind.fill,
            "fill",
            context -> context.serverOrThrow().fill(context, new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), state)
        );
        return this;
    }

    /** Replaces an inclusive box with air. */
    public ScenarioBuilder clearArea(int x1, int y1, int z1, int x2, int y2, int z2) {
        addServer(
            StepKind.clearArea,
            "clearArea",
            context -> context.serverOrThrow().clearArea(context, new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2))
        );
        return this;
    }

    /** Mutates a block entity on the server thread. */
    public ScenarioBuilder withBlockEntity(int x, int y, int z, Consumer<BlockEntity> action) {
        addServer(
            StepKind.withBlockEntity,
            "withBlockEntity " + x + "," + y + "," + z,
            context -> context.serverOrThrow().withBlockEntity(context, new BlockPos(x, y, z), action)
        );
        return this;
    }

    /** Moves the local player. */
    public ScenarioBuilder teleportPlayer(double x, double y, double z) {
        addServer(
            StepKind.teleportPlayer,
            "teleportPlayer",
            context -> context.serverOrThrow().teleportPlayer(context, x, y, z)
        );
        return this;
    }

    /** Aims the local player. */
    public ScenarioBuilder yawPitch(float yaw, float pitch) {
        addServer(StepKind.yawPitch, "yawPitch", context -> context.serverOrThrow().yawPitch(context, yaw, pitch));
        return this;
    }

    /** Puts a stack of an item into the local player's inventory. */
    public ScenarioBuilder giveItem(ResourceLocation item, int count) {
        addServer(
            StepKind.giveItem,
            "giveItem " + item,
            context -> context.serverOrThrow().giveItem(context, item, count)
        );
        return this;
    }

    /** Runs a server command. */
    public ScenarioBuilder runCommand(String command) {
        addServer(
            StepKind.runCommand,
            "command " + command,
            context -> context.serverOrThrow().runCommand(context, command)
        );
        return this;
    }

    /** Holds the clock until the integrated server has ticked a number of times. */
    public ScenarioBuilder serverTicks(int ticks) {
        addServer(
            StepKind.serverTicks,
            "serverTicks(" + ticks + ")",
            context -> context.serverOrThrow().serverTicks(context, ticks),
            0,
            (ticks * 50) + 5_000
        );
        return this;
    }

    /** Holds the clock until the client has a chunk at the given column. */
    public ScenarioBuilder awaitClientChunk(int x, int z) {
        add(StepKind.awaitClientChunk, "awaitClientChunk " + x + "," + z, context -> {
            if (!context.clientChunkLoaded(x, z)) {
                context.repeat("waiting for chunk " + x + "," + z);
            }
        }, 0, serverStepTimeoutMs);
        return this;
    }

    /**
     * Holds the clock until the client has a block entity at the given position.
     *
     * <p>A chunk arriving is not the same event as its block entities arriving: the client builds
     * them while it reads the chunk, so a structure placed server-side is visible as blocks one
     * frame earlier than as a block entity. A scenario that hands the client's block entity to an
     * assertion has to wait for the second event.
     */
    public ScenarioBuilder awaitClientBlockEntity(int x, int y, int z) {
        add(StepKind.awaitClientBlockEntity, "awaitClientBlockEntity " + x + "," + y + "," + z, context -> {
            if (!context.clientBlockEntityLoaded(x, y, z)) {
                context.repeat("waiting for the client block entity " + x + "," + y + "," + z);
            }
        }, 0, serverStepTimeoutMs);
        return this;
    }

    // endregion

    // region screens

    /** Shows a screen. {@code Minecraft#setScreen} runs its {@code init()}. */
    public ScenarioBuilder openScreen(Supplier<Screen> screen) {
        add(StepKind.openScreen, "openScreen", context -> context.minecraft().setScreen(screen.get()));
        return this;
    }

    /** Holds the clock until a screen of the given type is shown. */
    public ScenarioBuilder awaitScreen(Class<? extends Screen> type) {
        add(StepKind.awaitScreen, "awaitScreen " + type.getSimpleName(), context -> {
            if (!type.isInstance(context.screen())) {
                context.repeat("waiting for screen " + type.getSimpleName());
            }
        }, 0, serverStepTimeoutMs);
        return this;
    }

    /** Closes the current screen. */
    public ScenarioBuilder closeScreen() {
        add(StepKind.closeScreen, "closeScreen", context -> context.minecraft().setScreen(null));
        return this;
    }

    // endregion

    // region screen input

    /**
     * Moves the scripted pointer to a point in GUI-scaled screen space.
     *
     * <p>The point is where a screen's own hit testing decides what was touched, so it should be the
     * centre of the thing under test — a widget, a slot, a button — computed from the live layout on
     * the frame the step runs rather than frozen when the scenario was defined.
     */
    public ScenarioBuilder hover(double x, double y) {
        add(StepKind.hover, "hover " + x + "," + y, context -> hoverBody(context, x, y));
        return this;
    }

    /** Left clicks a point: hover, press, release. */
    public ScenarioBuilder click(double x, double y) {
        clickAt(x, y, 0);
        return this;
    }

    /** Right clicks a point. */
    public ScenarioBuilder rightClick(double x, double y) {
        clickAt(x, y, 1);
        return this;
    }

    /** Clicks a point with shift held as a real key, because held state is what the game reads. */
    public ScenarioBuilder shiftClick(double x, double y) {
        keyDown(GLFW.GLFW_KEY_LEFT_SHIFT);
        clickAt(x, y, 0);
        keyUp(GLFW.GLFW_KEY_LEFT_SHIFT);
        return this;
    }

    /**
     * Clicks a point twice, with the intermediate release-to-press gap well under the game's
     * double-click threshold. The threshold is wall-clock, so the gap is a settle, not a wait step.
     */
    public ScenarioBuilder doubleClick(double x, double y) {
        String label = "doubleClick " + x + "," + y;
        clickGesture(x, y, 0, label + ":first");
        int saved = pendingSettleMs;
        pendingSettleMs = doubleClickGapMs;
        clickGesture(x, y, 0, label + ":second");
        pendingSettleMs = saved;
        return this;
    }

    /** Clicks at raw GUI-scaled coordinates with an explicit button. */
    public ScenarioBuilder clickAt(double x, double y, int button) {
        clickGesture(x, y, button, "clickAt " + x + "," + y);
        return this;
    }

    /** Scrolls at a point. */
    public ScenarioBuilder scroll(double x, double y, double amount) {
        String label = "scroll " + x + "," + y + ":" + amount;
        add(StepKind.scroll, label + ":hover", context -> hoverBody(context, x, y));
        add(StepKind.scroll, label, context -> context.driver().scroll(context.screenOrThrow(), (float) x, (float) y, amount));
        return this;
    }

    /** Drags from one point onto another, as the eight frames a real drag takes. */
    public ScenarioBuilder drag(double fromX, double fromY, double toX, double toY) {
        dragGesture(fromX, fromY, toX, toY, "drag");
        return this;
    }

    /** Presses and releases a key with the given modifier mask held as real keys. */
    public ScenarioBuilder key(int keyCode, int modifiers) {
        int[] modifierKeys = modifierKeysOf(modifiers);
        for (int modifier : modifierKeys) {
            keyDown(modifier);
        }
        keyDown(keyCode);
        keyUp(keyCode);
        for (int i = modifierKeys.length - 1; i >= 0; i--) {
            keyUp(modifierKeys[i]);
        }
        return this;
    }

    /** Presses a key and leaves it down. */
    public ScenarioBuilder keyDown(int keyCode) {
        add(
            StepKind.keyDown,
            "keyDown " + keyName(keyCode),
            context -> context.driver().keyDown(context.screenOrThrow(), keyCode, scanCodeOf(keyCode), 0)
        );
        return this;
    }

    /**
     * Releases a key.
     *
     * <p>The release goes to whatever is listening when it arrives, which is not necessarily the
     * screen the press went to: a press can be the thing that closes its own screen (Escape closes a
     * chat box, Enter sends the line and closes it). Dispatching the release at
     * {@code screenOrThrow()} would turn that ordinary pair — and every scenario that closes a screen
     * with a key — into "no screen is open", so with no screen open the release takes the in-world
     * handler route instead, which is where a real release lands in that state. With a screen still
     * open nothing changes.
     */
    public ScenarioBuilder keyUp(int keyCode) {
        add(
            StepKind.keyUp,
            "keyUp " + keyName(keyCode),
            context -> releaseKey(context, keyCode)
        );
        return this;
    }

    /** Types a string, one character per step; {@code \n} presses enter and {@code \t} presses tab. */
    public ScenarioBuilder type(String text) {
        return type(text, 0);
    }

    /** Types a string with the given modifier mask. */
    public ScenarioBuilder type(String text, int modifiers) {
        text.codePoints().forEach(codePoint -> {
            if (codePoint == '\n') {
                key(GLFW.GLFW_KEY_ENTER, modifiers);
            } else if (codePoint == '\t') {
                key(GLFW.GLFW_KEY_TAB, modifiers);
            } else {
                add(
                    StepKind.type,
                    "type char " + codePoint,
                    context -> context.driver().charTyped(context.screenOrThrow(), codePoint, modifiers)
                );
            }
        });
        return this;
    }

    /**
     * Replaces the focused text field's content: select all through ctrl+A, delete, then type.
     *
     * <p>Focus is the screen's own business — the live screen decides which field a character
     * reaches — so this verb takes no target; it is the "overwrite whatever is focused" half of the
     * upstream {@code typeInto}.
     */
    public ScenarioBuilder typeInto(String text) {
        key(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL);
        key(GLFW.GLFW_KEY_BACKSPACE, 0);
        type(text);
        return this;
    }

    // endregion

    // region in-world player input

    /**
     * Turns the player by a delta in degrees through the real mouse-look path.
     *
     * <p>The rotation is applied by vanilla on the next frame — the turn pump runs before a step body
     * ever does — so a scenario that asserts on it waits a frame first, exactly as it would for a
     * mouse.
     */
    public ScenarioBuilder turn(float dYaw, float dPitch) {
        embody();
        add(StepKind.embodyTurn, "turn " + dYaw + "/" + dPitch, context -> context.playerInput().turnBy(dYaw, dPitch));
        return this;
    }

    /** Sweeps the look to an absolute pose over {@code durationTicks} client ticks. */
    public ScenarioBuilder lookSweep(float toYaw, float toPitch, int durationTicks) {
        return lookSweep(toYaw, toPitch, durationTicks, null);
    }

    /**
     * Sweeps the look to an absolute pose, running {@code perFrame} on each rendered frame of the
     * sweep.
     *
     * <p>The sweep is one step that repeats until the game clock has run its {@code durationTicks},
     * which is what makes the callback a per-frame one: a step attempt is a frame, and a repeating
     * step is not settled between attempts. The step may span any number of frames — the sweep is
     * measured in ticks, not attempts — so the callback sees every frame of the gesture.
     *
     * <p>{@code perFrame} runs with the step's context, so it can record a timeline entry per frame
     * and record checks that the report keeps per frame — the handle a scenario needs to assert on a
     * transition that happens <em>during</em> a gesture rather than at its end.
     */
    public ScenarioBuilder lookSweep(float toYaw, float toPitch, int durationTicks, @Nullable StepBody perFrame) {
        return lookSweep(() -> new float[]{toYaw, toPitch}, durationTicks, perFrame);
    }

    /**
     * Sweeps the look to a pose the scenario only knows once it is running.
     *
     * <p>The target is read on the first frame of the sweep, not when the scenario is defined: a pose
     * derived from a live reading — an aim that lands the crosshair on something the panel reports a
     * frame later — does not exist until that reading does. Passing it as a supplier is what keeps a
     * scenario from having to freeze a value it cannot know yet.
     */
    public ScenarioBuilder lookSweep(Supplier<float[]> target, int durationTicks, @Nullable StepBody perFrame) {
        embody();
        int ticks = Math.max(1, durationTicks);
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean finished = new AtomicBoolean();
        add(StepKind.embodyTurn, "lookSweep (" + ticks + " ticks)", context -> {
            PlayerInput player = context.playerInput();
            if (!started.getAndSet(true)) {
                float[] wanted = target.get();
                player.lookSweep(wanted[0], wanted[1], ticks);
            }
            // one attempt per frame, sampled one per frame: the sweep itself is advanced by the run's
            // frame hook, so this step only holds the clock still and records what each frame turned.
            if (perFrame != null) {
                perFrame.run(context);
            }
            if (player.sweeping()) {
                context.repeat("sweep tick " + ticks + " pending");
                return;
            }
            // the attempt after the sweep exists to let the last write land and be sampled before
            // the step settles, exactly as a per-frame sweep needed one extra attempt
            if (!finished.getAndSet(true)) {
                context.repeat("one frame for the sweep's last write");
            }
        }, 0, (ticks * 100) + 10_000);
        return this;
    }

    /**
     * The frame-counted sweep, converted at the tick rate: a frame reads as one twentieth of a
     * second, so {@code frames} frames are {@code frames} ticks.
     *
     * @deprecated use {@link #lookSweep(float, float, int)}, which names a duration rather than a
     *             frame count.
     */
    @Deprecated
    public ScenarioBuilder lookSweepFrames(float toYaw, float toPitch, int frames) {
        return lookSweepFrames(() -> new float[]{toYaw, toPitch}, frames, null);
    }

    /** @deprecated use {@link #lookSweep(float, float, int, StepBody)}. */
    @Deprecated
    public ScenarioBuilder lookSweepFrames(float toYaw, float toPitch, int frames, @Nullable StepBody perFrame) {
        return lookSweepFrames(() -> new float[]{toYaw, toPitch}, frames, perFrame);
    }

    /** @deprecated use {@link #lookSweep(Supplier, int, StepBody)}. */
    @Deprecated
    public ScenarioBuilder lookSweepFrames(Supplier<float[]> target, int frames, @Nullable StepBody perFrame) {
        return lookSweep(target, frames, perFrame);
    }

    /** Presses and releases a key through the real keyboard handler. */
    public ScenarioBuilder pressKey(int glfwKey) {
        embody();
        add(StepKind.embodyKey, "pressKey " + keyName(glfwKey), context -> context.playerInput().pressKey(glfwKey));
        return this;
    }

    /**
     * Presses and releases a key with the keys a modifier mask names held around it, all through the
     * real keyboard handler.
     *
     * <p>The modifier keys are held as real keys rather than passed as a mask alone, because a mod's
     * own bind asks the game whether the modifier "counts as held" — NeoForge's {@code KeyModifier}
     * answers through {@code Screen.hasControlDown()}, which reads the harness's virtual key state.
     * The mask is passed to the key press as well, so a handler that reads the event's modifiers
     * directly sees the same thing.
     *
     * <p>Both halves matter for a chord into an overlay that is not a widget of the open screen —
     * JEI's search bar is one — because that overlay only ever sees keys pressed through
     * {@code KeyboardHandler}, which is also where NeoForge raises its screen key events.
     */
    public ScenarioBuilder pressKey(int glfwKey, int modifiers) {
        embody();
        int[] modifierKeys = modifierKeysOf(modifiers);
        for (int modifier : modifierKeys) {
            holdKey(modifier);
        }
        add(
            StepKind.embodyKey,
            "pressKey " + keyName(glfwKey) + (modifierKeys.length == 0 ? "" : " + " + modifiers),
            context -> context.playerInput().pressKey(glfwKey, modifiers)
        );
        for (int i = modifierKeys.length - 1; i >= 0; i--) {
            releaseKey(modifierKeys[i]);
        }
        return this;
    }

    /** Presses a key and leaves it down through the real keyboard handler. */
    public ScenarioBuilder holdKey(int glfwKey) {
        embody();
        add(StepKind.embodyKey, "holdKey " + keyName(glfwKey), context -> context.playerInput().holdKey(glfwKey));
        return this;
    }

    /** Releases a key held by {@link #holdKey}. */
    public ScenarioBuilder releaseKey(int glfwKey) {
        embody();
        add(StepKind.embodyKey, "releaseKey " + keyName(glfwKey), context -> context.playerInput().releaseKey(glfwKey));
        return this;
    }

    /** Presses and releases the key a bind currently carries. */
    public ScenarioBuilder pressBind(Function<Options, KeyMapping> bind) {
        embody();
        add(StepKind.embodyKey, "pressBind " + bindName(bind), context -> context.playerInput().pressBind(bind));
        return this;
    }

    /** Holds a bind down — a real walk, jump or sneak that lasts until {@link #releaseBind}. */
    public ScenarioBuilder holdBind(Function<Options, KeyMapping> bind) {
        embody();
        add(StepKind.embodyKey, "holdBind " + bindName(bind), context -> context.playerInput().holdBind(bind));
        return this;
    }

    /** Releases a bind held by {@link #holdBind}. */
    public ScenarioBuilder releaseBind(Function<Options, KeyMapping> bind) {
        embody();
        add(StepKind.embodyKey, "releaseBind " + bindName(bind), context -> context.playerInput().releaseBind(bind));
        return this;
    }

    /** Selects a hotbar slot by pressing its keybind. */
    public ScenarioBuilder hotbarSlot(int slot) {
        embody();
        add(StepKind.embodyKey, "hotbarSlot " + slot, context -> context.playerInput().hotbarSlot(slot));
        return this;
    }

    /** Types a string through the real {@code charTyped} handler, one character per step. */
    public ScenarioBuilder typeText(String text) {
        embody();
        text.codePoints().forEach(
            codePoint -> add(
                StepKind.embodyType,
                "typeText char " + codePoint,
                context -> context.playerInput().typeText(new String(Character.toChars(codePoint)))
            )
        );
        return this;
    }

    /** Jumps: holds the jump bind for {@code ticks} ticks, then releases it. */
    public ScenarioBuilder jump(int ticks) {
        holdBind(options -> options.keyJump);
        ticks(ticks);
        releaseBind(options -> options.keyJump);
        return this;
    }

    /**
     * Starts or stops sneaking by holding the sneak bind.
     *
     * <p>A client that has "toggle crouch" on turns a press into a toggle, so a scenario that needs
     * sneak to be a hold — and to stop when it says so — pins
     * {@code options.toggleCrouch().set(false)} before the cycle.
     */
    public ScenarioBuilder sneak(boolean on) {
        if (on) {
            holdBind(options -> options.keyShift);
        } else {
            releaseBind(options -> options.keyShift);
        }
        return this;
    }

    /** Left clicks through the real handler: mines the block under the crosshair. */
    public ScenarioBuilder attack() {
        embody();
        add(StepKind.embodyClick, "attack", context -> context.playerInput().attack());
        return this;
    }

    /** Right clicks through the real handler. */
    public ScenarioBuilder useItem() {
        embody();
        add(StepKind.embodyClick, "useItem", context -> context.playerInput().useItem());
        return this;
    }

    /** Middle clicks through the real handler. */
    public ScenarioBuilder pickBlock() {
        embody();
        add(StepKind.embodyClick, "pickBlock", context -> context.playerInput().pickBlock());
        return this;
    }

    /** Presses a mouse button and leaves it down. */
    public ScenarioBuilder mouseDown(int button) {
        embody();
        add(StepKind.embodyClick, "mouseDown " + button, context -> context.playerInput().mouseDown(button));
        return this;
    }

    /** Releases a mouse button. */
    public ScenarioBuilder mouseUp(int button) {
        embody();
        add(StepKind.embodyClick, "mouseUp " + button, context -> context.playerInput().mouseUp(button));
        return this;
    }

    /** Scrolls a notch through the real handler. */
    public ScenarioBuilder scrollWorld(double amount) {
        embody();
        add(StepKind.embodyClick, "scroll " + amount, context -> context.playerInput().scroll(amount));
        return this;
    }

    /** Releases every hold the embodied input carries, without ending the embodiment. */
    public ScenarioBuilder releasePlayerInput() {
        embody();
        add(StepKind.embodyRelease, "release the embodied player input", context -> context.playerInput().releaseAll());
        return this;
    }

    /**
     * Marks the scenario as embodied and registers the teardown that lets the world go again.
     *
     * <p>A hold is a key left down across ticks, so it outlives the step that started it and would
     * otherwise walk the player through every scenario after this one. The teardown runs after the
     * scenario's steps even when it aborted, which is what makes the release unconditional.
     */
    private void embody() {
        if (embodied) return;
        embodied = true;
        addTo(
            teardownSteps,
            StepKind.embodyRelease,
            "release the embodied player input",
            context -> context.playerInput().releaseAll()
        );
    }

    /** The name of the bind a lookup resolves to, for the step's own name. */
    private static String bindName(Function<Options, KeyMapping> bind) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return "bind";
        KeyMapping mapping = bind.apply(minecraft.options);
        return mapping == null ? "bind" : mapping.getName();
    }

    // endregion

    // region waiting

    /** Holds the clock for a number of rendered frames. */
    public ScenarioBuilder frames(int frames) {
        AtomicInteger remaining = new AtomicInteger(frames);
        add(StepKind.waitFrames, "frames(" + frames + ")", context -> {
            if (remaining.decrementAndGet() > 0) {
                context.repeat("waiting " + remaining.get() + " frames");
            }
        }, 0, (frames * 50) + 5_000);
        return this;
    }

    /** Holds the clock for a number of client ticks. */
    public ScenarioBuilder ticks(int ticks) {
        AtomicLong anchor = new AtomicLong(-1L);
        add(StepKind.waitTicks, "ticks(" + ticks + ")", context -> {
            long gameTime = context.gameTime();
            if (gameTime < 0L) {
                context.repeat("waiting for a level");
                return;
            }
            if (anchor.get() < 0L) {
                anchor.set(gameTime);
            }
            long target = anchor.get() + ticks;
            if (gameTime < target) {
                context.repeat("waiting " + (target - gameTime) + " ticks");
            }
        }, 0, (ticks * 100) + 5_000);
        return this;
    }

    /** Holds the clock for a wall-clock time. */
    public ScenarioBuilder waitMs(long millis) {
        AtomicLong anchor = new AtomicLong(-1L);
        int budget = (int) Math.min(millis + 5_000L, Integer.MAX_VALUE);
        add(StepKind.waitMs, "waitMs(" + millis + ")", context -> {
            long now = System.currentTimeMillis();
            if (anchor.get() < 0L) {
                anchor.set(now);
            }
            long remaining = anchor.get() + millis - now;
            if (remaining > 0L) {
                context.repeat("waiting " + remaining + " ms");
            }
        }, 0, budget);
        return this;
    }

    /** Holds the clock until a client-side condition holds. */
    public ScenarioBuilder waitUntil(String reason, BooleanSupplier condition) {
        add(StepKind.waitUntil, "waitUntil " + reason, context -> {
            if (!condition.getAsBoolean()) {
                context.repeat(reason);
            }
        });
        return this;
    }

    /** Holds the clock until a condition holds on the integrated server thread. */
    public ScenarioBuilder waitUntilServer(String reason, BooleanSupplier condition) {
        add(
            StepKind.waitUntilServer,
            "waitUntilServer " + reason,
            context -> context.serverOrThrow().poll(context, reason, condition::getAsBoolean)
        );
        return this;
    }

    // endregion

    // region assertions

    /** Records a named assertion. */
    public ScenarioBuilder check(String label, BooleanSupplier condition) {
        add(StepKind.check, "check " + label, context -> context.check(label, condition.getAsBoolean()));
        return this;
    }

    /**
     * Records a named boolean assertion with the observed value beside it.
     *
     * <p>The detail is read on the frame the step runs, so a failure in the report says what was
     * actually seen instead of only that the condition did not hold — the difference between a red
     * step that can be diagnosed from the report and one that has to be re-run under a debugger.
     */
    public ScenarioBuilder check(String label, BooleanSupplier condition, Supplier<String> detail) {
        add(StepKind.check, "check " + label, context -> context.check(label, condition.getAsBoolean(), detail.get()));
        return this;
    }

    /** Records an equality assertion. */
    public ScenarioBuilder checkEquals(String label, @Nullable Object expected, Supplier<Object> actual) {
        add(
            StepKind.checkEquals,
            "checkEquals " + label,
            context -> context.checkEquals(label, expected, actual.get())
        );
        return this;
    }

    /**
     * Records whether the scenario's own state holds anything under a key.
     *
     * <p>The state is where a step hands a value to a later step — {@code state().put} in an action,
     * read back by the assertion that follows.
     */
    public ScenarioBuilder checkStatePresent(String label, String stateKey) {
        add(
            StepKind.check,
            "checkStatePresent " + label,
            context -> context.check(
                label,
                context.state().containsKey(stateKey),
                "state." + stateKey + "=" + context.state().get(stateKey)
            )
        );
        return this;
    }

    /** Records an equality assertion between the scenario's state and a value computed now. */
    public ScenarioBuilder checkEqualsState(String label, String stateKey, Supplier<Object> expected) {
        add(
            StepKind.checkEquals,
            "checkEqualsState " + label,
            context -> context.checkEquals(label, expected.get(), context.state().get(stateKey))
        );
        return this;
    }

    /** Records the type of the shown screen. */
    public ScenarioBuilder checkScreen(Class<? extends Screen> type) {
        add(StepKind.checkScreen, "checkScreen " + type.getSimpleName(), context -> {
            Screen screen = context.screen();
            context.check(
                "screen " + type.getSimpleName(),
                type.isInstance(screen),
                screen == null ? "no screen" : screen.getClass().getSimpleName()
            );
        });
        return this;
    }

    /**
     * Records whether the capture with the given label has been written.
     *
     * <p>The pipeline writes one queued request per frame, so pair this with the {@code screenshot}
     * step that queued it — one step later the file is on disk. Also fails when the file is missing,
     * which is what catches a capture the pipeline dropped.
     */
    public ScenarioBuilder checkCaptured(String label) {
        add(StepKind.checkCapture, "checkCaptured " + label, context -> {
            Path path = context.writtenCapture(label);
            context.check(
                "capture " + label,
                path != null && Files.isRegularFile(path),
                path == null ? "nothing queued or written under this label yet" : path.toString()
            );
        });
        return this;
    }

    // endregion

    // region captures

    /** Queues a full-frame screenshot. */
    public ScenarioBuilder screenshot(String label) {
        add(StepKind.screenshot, "screenshot " + label, context -> context.screenshot(label));
        return this;
    }

    /**
     * Queues a readback of an arbitrary colour surface — a Minecraft render target, declared as the
     * supplier of the target to read.
     *
     * <p>The supplier is resolved on the frame the capture is serviced, so it can follow the frame's
     * own state; a supplier that yields nothing readable this implementation logs the skip and writes
     * no file, so pair the capture with {@link #checkCaptured(String)} only when it should exist.
     */
    public ScenarioBuilder screenshotSurface(String label, Supplier<?> surface) {
        add(
            StepKind.screenshotSurface,
            "screenshotSurface " + label,
            context -> context.screenshotSurface(label, surface)
        );
        return this;
    }

    /**
     * Queues {@code frames} consecutive full-frame captures, one per frame, written as
     * {@code <label>_%04d.png}, and then waits for the queue to drain.
     *
     * <p>This declares two steps: the queueing one, and the wait — the pipeline services one request
     * per frame, so the images only exist after that many frames have passed. Assert on the run with
     * {@link #checkCapturedSequence(String, int)} after it.
     */
    public ScenarioBuilder screenshotSequence(String label, int frames) {
        add(
            StepKind.screenshotSequence,
            "screenshotSequence " + label + "(" + frames + ")",
            context -> context.screenshotSequence(label, frames)
        );
        frames(frames + 2);
        return this;
    }

    /** Records whether every frame of a sequence has been written. */
    public ScenarioBuilder checkCapturedSequence(String label, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            checkCaptured(TestContext.sequenceFrameLabel(label, frame));
        }
        return this;
    }

    // endregion

    // region match recording

    /** Turns the match recorder on or off; the engine leaves it on for the whole of a harness run. */
    public ScenarioBuilder recordMatches(boolean enable) {
        add(StepKind.action, "recordMatches " + enable, context -> {
            MatchRecorder.setEnabled(enable);
            context.log(MatchRecorder.stats());
        });
        return this;
    }

    /** Drops every comparison the recorder holds, so the next assertion sees only what follows. */
    public ScenarioBuilder clearRecordedMatches() {
        add(StepKind.action, "clearRecordedMatches", context -> {
            MatchRecorder.clear();
            context.log("match recorder cleared");
        });
        return this;
    }

    /**
     * Asserts what the match recorder holds.
     *
     * <p>With {@code expect} set — the normal case — the step waits up to {@code withinMs} for a
     * recorded comparison the filter accepts, and fails only when the budget runs out, so a search
     * that lands a few frames after the keystroke is not a false red. With {@code expect} clear the
     * step cannot wait (nothing arriving would make a wait succeed), so it decides on one attempt:
     * failing the moment a recorded comparison <em>does</em> satisfy the filter.
     *
     * <p>A failure reports the tail of what was recorded, not just that nothing matched, because the
     * difference between "the engine never saw this query" and "the engine saw it and answered no" is
     * the whole diagnosis.
     *
     * @param filter   which recorded comparisons count
     * @param expect   whether such a comparison must exist
     * @param withinMs how long to wait for one, or {@code 0} for the default
     */
    public ScenarioBuilder assertMatchRecorded(MatchFilter filter, boolean expect, int withinMs) {
        int budget = withinMs > 0 ? withinMs : defaultMatchWindowMs;
        AtomicLong anchor = new AtomicLong(-1L);
        String label = "match recorded " + (expect ? "for " : "for none of ") + filter.describe();
        add(
            StepKind.check,
            "assertMatchRecorded " + (expect ? "" : "not ") + filter.describe(),
            context -> {
                long now = System.currentTimeMillis();
                if (anchor.get() < 0L) {
                    anchor.set(now);
                }
                MatchRecorder.Record found = MatchRecorder.newestMatching(filter);
                if (!expect) {
                    context.check(
                        label,
                        found == null,
                        found == null ? "nothing matched" : "but " + found.describe() + " was recorded"
                    );
                    return;
                }
                if (found != null) {
                    context.check(label, true, found.describe());
                    return;
                }
                if (now - anchor.get() < budget) {
                    context.repeat("waiting " + budget + " ms for " + filter.describe());
                    return;
                }
                context.check(label, false, MatchRecorder.describeMiss(filter));
            },
            0,
            budget + matchWindowSlackMs
        );
        return this;
    }

    // endregion

    // region timeline

    /**
     * Appends a line to the scenario's timeline log — one JSON record per call, next to the report.
     *
     * <p>The blob is read on the frame the step runs, so a supplier can hand it the engine's own dump
     * line or any other per-frame reading. Appending is a no-op when the run writes no timeline.
     */
    public ScenarioBuilder timelineRecord(String name, Supplier<String> blob) {
        add(StepKind.timelineRecord, "timelineRecord " + name, context -> context.recordTimeline(name, blob.get()));
        return this;
    }

    /** Appends one timeline record per frame for {@code frames} frames. */
    public ScenarioBuilder timelineFrames(String name, int frames, Supplier<String> blob) {
        AtomicInteger remaining = new AtomicInteger(frames);
        add(StepKind.timelineRecord, "timelineFrames " + name + "(" + frames + ")", context -> {
            if (remaining.getAndDecrement() <= 0) return;
            context.recordTimeline(name, blob.get());
            if (remaining.get() > 0) {
                context.repeat("timeline " + (frames - remaining.get()) + "/" + frames);
            }
        }, 0, (frames * 250) + 10_000);
        return this;
    }

    // endregion

    // region internals

    private ScenarioBuilder inList(List<Step> target, String groupName, Runnable body) {
        groups.addLast(groupName);
        targets.addLast(target);
        try {
            body.run();
        } finally {
            groups.removeLast();
            targets.removeLast();
        }
        return this;
    }

    private Step add(StepKind kind, String name, StepBody body) {
        return add(kind, name, body, pendingSettleMs, pendingTimeoutMs);
    }

    /** Adds one step to an explicit list, bypassing the target stack — teardown's deferred step. */
    private Step addTo(List<Step> target, StepKind kind, String name, StepBody body) {
        int index = target == steps ? target.size() : indexBase(target) + target.size();
        Step step = new Step(index, kind, name, currentGroup(), pendingSettleMs, pendingTimeoutMs, body);
        target.add(step);
        return step;
    }

    private Step add(StepKind kind, String name, StepBody body, int settleMs, int timeoutMs) {
        List<Step> target = targets.getLast();
        int index = target == steps ? target.size() : indexBase(target) + target.size();
        Step step = new Step(index, kind, name, currentGroup(), settleMs, timeoutMs, body);
        target.add(step);
        return step;
    }

    private void addServer(StepKind kind, String name, StepBody body) {
        add(kind, name, body, pendingSettleMs, serverStepTimeoutMs);
    }

    private void addServer(StepKind kind, String name, StepBody body, int settleMs, int timeoutMs) {
        add(kind, name, body, settleMs, timeoutMs);
    }

    private int indexBase(List<Step> target) {
        return target == teardownSteps ? teardownIndexBase : serverTeardownIndexBase;
    }

    private @Nullable String currentGroup() {
        return groups.isEmpty() ? null : String.join("/", groups);
    }

    private void clickGesture(double x, double y, int button, String label) {
        add(StepKind.click, label + ":hover", context -> hoverBody(context, x, y));
        add(
            StepKind.click,
            label + ":press",
            context -> context.driver().mouseDown(context.screenOrThrow(), (float) x, (float) y, button)
        );
        add(
            StepKind.click,
            label + ":release",
            context -> context.driver().mouseUp(context.screenOrThrow(), (float) x, (float) y, button)
        );
    }

    private static void hoverBody(TestContext context, double x, double y) {
        context.driver().placeCursor((float) x, (float) y);
        context.driver().moveTo(context.screenOrThrow(), (float) x, (float) y);
    }

    /**
     * The eight frames of a drag: aim, press, nudge inside the source, leave, move, arrive, settle
     * while still over the target, and drop.
     */
    private void dragGesture(double fromX, double fromY, double toX, double toY, String label) {
        String tag = label + " " + fromX + "," + fromY + "->" + toX + "," + toY;
        add(StepKind.drag, tag + ":aim", context -> {
            context.state().put(dragLastX, (float) fromX);
            context.state().put(dragLastY, (float) fromY);
            hoverBody(context, fromX, fromY);
        });
        add(
            StepKind.drag,
            tag + ":press",
            context -> context.driver().mouseDown(context.screenOrThrow(), (float) fromX, (float) fromY, 0)
        );
        add(StepKind.drag, tag + ":nudge", context -> dispatchDrag(context, (float) fromX + 1f, (float) fromY + 1f));
        add(StepKind.drag, tag + ":leave", context -> dispatchDrag(context, lerp(fromX, toX, 0.35f), lerp(fromY, toY, 0.35f)));
        add(StepKind.drag, tag + ":move", context -> dispatchDrag(context, lerp(fromX, toX, 0.75f), lerp(fromY, toY, 0.75f)));
        add(StepKind.drag, tag + ":arrive", context -> dispatchDrag(context, (float) toX, (float) toY));
        add(StepKind.drag, tag + ":settle", context -> dispatchDrag(context, (float) toX + 1f, (float) toY));
        add(
            StepKind.drag,
            tag + ":drop",
            context -> context.driver().mouseUp(context.screenOrThrow(), (float) toX, (float) toY, 0)
        );
    }

    private static float lerp(double from, double to, float t) {
        return (float) (from + ((to - from) * t));
    }

    /**
     * Delivers one key release to whatever is listening: the open screen, or the world.
     *
     * <p>With a screen open the release is the screen's, exactly as the press was. With none it goes
     * through the in-world handler route ({@code KeyboardHandler.keyPress}), which is the path a real
     * release takes once the screen that consumed the press has closed — and which is also what
     * clears the harness's own record of the hold. A release with neither a screen nor a player has
     * nothing that could receive it, and vanilla would drop it too; that case is written into the
     * step's log rather than left to look like a key that was never released.
     */
    private static void releaseKey(TestContext context, int keyCode) {
        Screen screen = context.screen();
        if (screen != null) {
            context.driver().keyUp(screen, keyCode, scanCodeOf(keyCode), 0);
            return;
        }
        if (Minecraft.getInstance().player == null) {
            context.log("keyUp " + keyName(keyCode) + ": no screen and no player, so nothing received it");
            return;
        }
        context.playerInput().releaseKey(keyCode);
    }

    private static void dispatchDrag(TestContext context, float x, float y) {
        float lastX = (Float) context.state().getOrDefault(dragLastX, x);
        float lastY = (Float) context.state().getOrDefault(dragLastY, y);
        context.driver().dragTo(context.screenOrThrow(), x, y, 0, x - lastX, y - lastY);
        context.state().put(dragLastX, x);
        context.state().put(dragLastY, y);
    }

    private static int[] modifierKeysOf(int modifiers) {
        List<Integer> keys = new ArrayList<>();
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) keys.add(GLFW.GLFW_KEY_LEFT_SHIFT);
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) keys.add(GLFW.GLFW_KEY_LEFT_CONTROL);
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) keys.add(GLFW.GLFW_KEY_LEFT_ALT);
        if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0) keys.add(GLFW.GLFW_KEY_LEFT_SUPER);
        int[] result = new int[keys.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = keys.get(i);
        }
        return result;
    }

    private static int scanCodeOf(int keyCode) {
        return GLFW.glfwGetKeyScancode(keyCode);
    }

    private static String keyName(int keyCode) {
        String name = GLFW.glfwGetKeyName(keyCode, 0);
        return name == null ? Integer.toString(keyCode) : name;
    }

    // endregion
}
