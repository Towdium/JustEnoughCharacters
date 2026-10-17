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

public final class ScenarioBuilder {

    private static final int teardownIndexBase = 1_000;

    private static final int serverTeardownIndexBase = 2_000;

    private static final int serverStepTimeoutMs = 60_000;

    private static final String dragLastX = "jech.uitest.drag.lastX";

    private static final String dragLastY = "jech.uitest.drag.lastY";

    private static final int doubleClickGapMs = 30;

    private static final int defaultMatchWindowMs = 3_000;

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

    public List<Step> steps() {
        return List.copyOf(steps);
    }

    public List<Step> teardownSteps() {
        return List.copyOf(teardownSteps);
    }

    public List<Step> serverTeardownSteps() {
        return List.copyOf(serverTeardownSteps);
    }

    public ScenarioOptions options() {
        return options;
    }

    public ScenarioBuilder guiScale(int scale) {
        options = options.withGuiScale(scale);
        return this;
    }

    public ScenarioBuilder hideGui(boolean hide) {
        options = options.withHideGui(hide);
        return this;
    }

    public ScenarioBuilder settleMs(int ms) {
        pendingSettleMs = ms;
        return this;
    }

    public ScenarioBuilder timeoutMs(int ms) {
        pendingTimeoutMs = ms;
        return this;
    }

    public ScenarioBuilder group(String name, Runnable body) {
        groups.addLast(name);
        try {
            body.run();
        } finally {
            groups.removeLast();
        }
        return this;
    }

    public ScenarioBuilder repeat(int times, Runnable body) {
        for (int i = 0; i < times; i++) {
            group("repeat " + (i + 1), body);
        }
        return this;
    }

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

    public ScenarioBuilder teardownServer(Runnable body) {
        return inList(serverTeardownSteps, "teardownServer", body);
    }

    public ScenarioBuilder step(StepKind kind, String name, StepBody body) {
        add(kind, name, body);
        return this;
    }

    public ScenarioBuilder log(String line) {
        add(StepKind.log, "log", context -> context.log(line));
        return this;
    }

    public ScenarioBuilder action(String name, Consumer<TestContext> body) {
        add(StepKind.action, "action " + name, body::accept);
        return this;
    }

    // endregion

    // region world and server

    public ScenarioBuilder setBlock(int x, int y, int z, BlockState state) {
        addServer(
            StepKind.setBlock,
            "setBlock " + x + "," + y + "," + z,
            context -> context.serverOrThrow().setBlock(context, new BlockPos(x, y, z), state)
        );
        return this;
    }

    public ScenarioBuilder fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        addServer(
            StepKind.fill,
            "fill",
            context -> context.serverOrThrow().fill(context, new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), state)
        );
        return this;
    }

    public ScenarioBuilder clearArea(int x1, int y1, int z1, int x2, int y2, int z2) {
        addServer(
            StepKind.clearArea,
            "clearArea",
            context -> context.serverOrThrow().clearArea(context, new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2))
        );
        return this;
    }

    public ScenarioBuilder withBlockEntity(int x, int y, int z, Consumer<BlockEntity> action) {
        addServer(
            StepKind.withBlockEntity,
            "withBlockEntity " + x + "," + y + "," + z,
            context -> context.serverOrThrow().withBlockEntity(context, new BlockPos(x, y, z), action)
        );
        return this;
    }

    public ScenarioBuilder teleportPlayer(double x, double y, double z) {
        addServer(
            StepKind.teleportPlayer,
            "teleportPlayer",
            context -> context.serverOrThrow().teleportPlayer(context, x, y, z)
        );
        return this;
    }

    public ScenarioBuilder yawPitch(float yaw, float pitch) {
        addServer(StepKind.yawPitch, "yawPitch", context -> context.serverOrThrow().yawPitch(context, yaw, pitch));
        return this;
    }

    public ScenarioBuilder giveItem(ResourceLocation item, int count) {
        addServer(
            StepKind.giveItem,
            "giveItem " + item,
            context -> context.serverOrThrow().giveItem(context, item, count)
        );
        return this;
    }

    public ScenarioBuilder runCommand(String command) {
        addServer(
            StepKind.runCommand,
            "command " + command,
            context -> context.serverOrThrow().runCommand(context, command)
        );
        return this;
    }

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

    public ScenarioBuilder awaitClientChunk(int x, int z) {
        add(StepKind.awaitClientChunk, "awaitClientChunk " + x + "," + z, context -> {
            if (!context.clientChunkLoaded(x, z)) {
                context.repeat("waiting for chunk " + x + "," + z);
            }
        }, 0, serverStepTimeoutMs);
        return this;
    }

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

    public ScenarioBuilder openScreen(Supplier<Screen> screen) {
        add(StepKind.openScreen, "openScreen", context -> context.minecraft().setScreen(screen.get()));
        return this;
    }

    public ScenarioBuilder awaitScreen(Class<? extends Screen> type) {
        add(StepKind.awaitScreen, "awaitScreen " + type.getSimpleName(), context -> {
            if (!type.isInstance(context.screen())) {
                context.repeat("waiting for screen " + type.getSimpleName());
            }
        }, 0, serverStepTimeoutMs);
        return this;
    }

    public ScenarioBuilder closeScreen() {
        add(StepKind.closeScreen, "closeScreen", context -> context.minecraft().setScreen(null));
        return this;
    }

    // endregion

    // region screen input

    public ScenarioBuilder hover(double x, double y) {
        add(StepKind.hover, "hover " + x + "," + y, context -> hoverBody(context, x, y));
        return this;
    }

    public ScenarioBuilder click(double x, double y) {
        clickAt(x, y, 0);
        return this;
    }

    public ScenarioBuilder rightClick(double x, double y) {
        clickAt(x, y, 1);
        return this;
    }

    public ScenarioBuilder shiftClick(double x, double y) {
        keyDown(GLFW.GLFW_KEY_LEFT_SHIFT);
        clickAt(x, y, 0);
        keyUp(GLFW.GLFW_KEY_LEFT_SHIFT);
        return this;
    }

    public ScenarioBuilder doubleClick(double x, double y) {
        String label = "doubleClick " + x + "," + y;
        clickGesture(x, y, 0, label + ":first");
        int saved = pendingSettleMs;
        pendingSettleMs = doubleClickGapMs;
        clickGesture(x, y, 0, label + ":second");
        pendingSettleMs = saved;
        return this;
    }

    public ScenarioBuilder clickAt(double x, double y, int button) {
        clickGesture(x, y, button, "clickAt " + x + "," + y);
        return this;
    }

    public ScenarioBuilder clickMouse(double x, double y) {
        return clickMouse(x, y, 0);
    }

    public ScenarioBuilder clickMouse(double x, double y, int button) {
        String label = "clickMouse " + x + "," + y;
        add(StepKind.click, label + ":hover", context -> hoverBody(context, x, y));
        add(
            StepKind.click,
            label + ":press",
            context -> context.driver()
                    .mouseDownThroughHandler(context.screenOrThrow(), (float) x, (float) y, button)
        );
        add(
            StepKind.click,
            label + ":release",
            context -> context.driver()
                    .mouseUpThroughHandler(context.screenOrThrow(), (float) x, (float) y, button)
        );
        return this;
    }

    public ScenarioBuilder scroll(double x, double y, double amount) {
        String label = "scroll " + x + "," + y + ":" + amount;
        add(StepKind.scroll, label + ":hover", context -> hoverBody(context, x, y));
        add(StepKind.scroll, label, context -> context.driver().scroll(context.screenOrThrow(), (float) x, (float) y, amount));
        return this;
    }

    public ScenarioBuilder drag(double fromX, double fromY, double toX, double toY) {
        dragGesture(fromX, fromY, toX, toY, "drag");
        return this;
    }

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

    public ScenarioBuilder keyDown(int keyCode) {
        add(
            StepKind.keyDown,
            "keyDown " + keyName(keyCode),
            context -> context.driver().keyDown(context.screenOrThrow(), keyCode, scanCodeOf(keyCode), 0)
        );
        return this;
    }

    public ScenarioBuilder keyUp(int keyCode) {
        add(
            StepKind.keyUp,
            "keyUp " + keyName(keyCode),
            context -> releaseKey(context, keyCode)
        );
        return this;
    }

    public ScenarioBuilder type(String text) {
        return type(text, 0);
    }

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

    public ScenarioBuilder typeInto(String text) {
        key(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL);
        key(GLFW.GLFW_KEY_BACKSPACE, 0);
        type(text);
        return this;
    }

    // endregion

    // region in-world player input

    public ScenarioBuilder turn(float dYaw, float dPitch) {
        embody();
        add(StepKind.embodyTurn, "turn " + dYaw + "/" + dPitch, context -> context.playerInput().turnBy(dYaw, dPitch));
        return this;
    }

    public ScenarioBuilder lookSweep(float toYaw, float toPitch, int durationTicks) {
        return lookSweep(toYaw, toPitch, durationTicks, null);
    }

    public ScenarioBuilder lookSweep(float toYaw, float toPitch, int durationTicks, @Nullable StepBody perFrame) {
        return lookSweep(() -> new float[]{toYaw, toPitch}, durationTicks, perFrame);
    }

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
            if (perFrame != null) {
                perFrame.run(context);
            }
            if (player.sweeping()) {
                context.repeat("sweep tick " + ticks + " pending");
                return;
            }
            if (!finished.getAndSet(true)) {
                context.repeat("one frame for the sweep's last write");
            }
        }, 0, (ticks * 100) + 10_000);
        return this;
    }

    @Deprecated
    public ScenarioBuilder lookSweepFrames(float toYaw, float toPitch, int frames) {
        return lookSweepFrames(() -> new float[]{toYaw, toPitch}, frames, null);
    }

    @Deprecated
    public ScenarioBuilder lookSweepFrames(float toYaw, float toPitch, int frames, @Nullable StepBody perFrame) {
        return lookSweepFrames(() -> new float[]{toYaw, toPitch}, frames, perFrame);
    }

    @Deprecated
    public ScenarioBuilder lookSweepFrames(Supplier<float[]> target, int frames, @Nullable StepBody perFrame) {
        return lookSweep(target, frames, perFrame);
    }

    public ScenarioBuilder pressKey(int glfwKey) {
        embody();
        add(StepKind.embodyKey, "pressKey " + keyName(glfwKey), context -> context.playerInput().pressKey(glfwKey));
        return this;
    }

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

    public ScenarioBuilder holdKey(int glfwKey) {
        embody();
        add(StepKind.embodyKey, "holdKey " + keyName(glfwKey), context -> context.playerInput().holdKey(glfwKey));
        return this;
    }

    public ScenarioBuilder releaseKey(int glfwKey) {
        embody();
        add(StepKind.embodyKey, "releaseKey " + keyName(glfwKey), context -> context.playerInput().releaseKey(glfwKey));
        return this;
    }

    public ScenarioBuilder pressBind(Function<Options, KeyMapping> bind) {
        embody();
        add(StepKind.embodyKey, "pressBind " + bindName(bind), context -> context.playerInput().pressBind(bind));
        return this;
    }

    public ScenarioBuilder holdBind(Function<Options, KeyMapping> bind) {
        embody();
        add(StepKind.embodyKey, "holdBind " + bindName(bind), context -> context.playerInput().holdBind(bind));
        return this;
    }

    public ScenarioBuilder releaseBind(Function<Options, KeyMapping> bind) {
        embody();
        add(StepKind.embodyKey, "releaseBind " + bindName(bind), context -> context.playerInput().releaseBind(bind));
        return this;
    }

    public ScenarioBuilder hotbarSlot(int slot) {
        embody();
        add(StepKind.embodyKey, "hotbarSlot " + slot, context -> context.playerInput().hotbarSlot(slot));
        return this;
    }

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

    public ScenarioBuilder jump(int ticks) {
        holdBind(options -> options.keyJump);
        ticks(ticks);
        releaseBind(options -> options.keyJump);
        return this;
    }

    public ScenarioBuilder sneak(boolean on) {
        if (on) {
            holdBind(options -> options.keyShift);
        } else {
            releaseBind(options -> options.keyShift);
        }
        return this;
    }

    public ScenarioBuilder attack() {
        embody();
        add(StepKind.embodyClick, "attack", context -> context.playerInput().attack());
        return this;
    }

    public ScenarioBuilder useItem() {
        embody();
        add(StepKind.embodyClick, "useItem", context -> context.playerInput().useItem());
        return this;
    }

    public ScenarioBuilder pickBlock() {
        embody();
        add(StepKind.embodyClick, "pickBlock", context -> context.playerInput().pickBlock());
        return this;
    }

    public ScenarioBuilder mouseDown(int button) {
        embody();
        add(StepKind.embodyClick, "mouseDown " + button, context -> context.playerInput().mouseDown(button));
        return this;
    }

    public ScenarioBuilder mouseUp(int button) {
        embody();
        add(StepKind.embodyClick, "mouseUp " + button, context -> context.playerInput().mouseUp(button));
        return this;
    }

    public ScenarioBuilder scrollWorld(double amount) {
        embody();
        add(StepKind.embodyClick, "scroll " + amount, context -> context.playerInput().scroll(amount));
        return this;
    }

    public ScenarioBuilder releasePlayerInput() {
        embody();
        add(StepKind.embodyRelease, "release the embodied player input", context -> context.playerInput().releaseAll());
        return this;
    }

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

    private static String bindName(Function<Options, KeyMapping> bind) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return "bind";
        KeyMapping mapping = bind.apply(minecraft.options);
        return mapping == null ? "bind" : mapping.getName();
    }

    // endregion

    // region waiting

    public ScenarioBuilder frames(int frames) {
        AtomicInteger remaining = new AtomicInteger(frames);
        add(StepKind.waitFrames, "frames(" + frames + ")", context -> {
            if (remaining.decrementAndGet() > 0) {
                context.repeat("waiting " + remaining.get() + " frames");
            }
        }, 0, (frames * 50) + 5_000);
        return this;
    }

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

    public ScenarioBuilder waitUntil(String reason, BooleanSupplier condition) {
        add(StepKind.waitUntil, "waitUntil " + reason, context -> {
            if (!condition.getAsBoolean()) {
                context.repeat(reason);
            }
        });
        return this;
    }

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

    public ScenarioBuilder check(String label, BooleanSupplier condition) {
        add(StepKind.check, "check " + label, context -> context.check(label, condition.getAsBoolean()));
        return this;
    }

    public ScenarioBuilder check(String label, BooleanSupplier condition, Supplier<String> detail) {
        add(StepKind.check, "check " + label, context -> context.check(label, condition.getAsBoolean(), detail.get()));
        return this;
    }

    public ScenarioBuilder checkEquals(String label, @Nullable Object expected, Supplier<Object> actual) {
        add(
            StepKind.checkEquals,
            "checkEquals " + label,
            context -> context.checkEquals(label, expected, actual.get())
        );
        return this;
    }

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

    public ScenarioBuilder checkEqualsState(String label, String stateKey, Supplier<Object> expected) {
        add(
            StepKind.checkEquals,
            "checkEqualsState " + label,
            context -> context.checkEquals(label, expected.get(), context.state().get(stateKey))
        );
        return this;
    }

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

    public ScenarioBuilder screenshot(String label) {
        add(StepKind.screenshot, "screenshot " + label, context -> context.screenshot(label));
        return this;
    }

    public ScenarioBuilder screenshotSurface(String label, Supplier<?> surface) {
        add(
            StepKind.screenshotSurface,
            "screenshotSurface " + label,
            context -> context.screenshotSurface(label, surface)
        );
        return this;
    }

    public ScenarioBuilder screenshotSequence(String label, int frames) {
        add(
            StepKind.screenshotSequence,
            "screenshotSequence " + label + "(" + frames + ")",
            context -> context.screenshotSequence(label, frames)
        );
        frames(frames + 2);
        return this;
    }

    public ScenarioBuilder checkCapturedSequence(String label, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            checkCaptured(TestContext.sequenceFrameLabel(label, frame));
        }
        return this;
    }

    // endregion

    // region match recording

    public ScenarioBuilder recordMatches(boolean enable) {
        add(StepKind.action, "recordMatches " + enable, context -> {
            MatchRecorder.setEnabled(enable);
            context.log(MatchRecorder.stats());
        });
        return this;
    }

    public ScenarioBuilder clearRecordedMatches() {
        add(StepKind.action, "clearRecordedMatches", context -> {
            MatchRecorder.clear();
            context.log("match recorder cleared");
        });
        return this;
    }

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

    public ScenarioBuilder timelineRecord(String name, Supplier<String> blob) {
        add(StepKind.timelineRecord, "timelineRecord " + name, context -> context.recordTimeline(name, blob.get()));
        return this;
    }

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
