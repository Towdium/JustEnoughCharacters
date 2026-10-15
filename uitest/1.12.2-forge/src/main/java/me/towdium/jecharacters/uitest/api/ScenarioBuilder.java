package me.towdium.jecharacters.uitest.api;

import me.towdium.jecharacters.uitest.input.CursorState;
import me.towdium.jecharacters.uitest.input.KeyState;
import me.towdium.jecharacters.uitest.match.MatchFilter;
import me.towdium.jecharacters.uitest.match.MatchRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What a scenario's {@code define} method is handed: the verbs its steps are built from.
 *
 * <p><b>Port note.</b> This is the 1.12.2 subset of the 1.21.1 builder, and the reduction is real
 * rather than cosmetic. On 1.21.1 a step routes its input through an {@link me.towdium.jecharacters
 * .uitest.api.InputMode}-selected {@code InputDriver} that dispatches at a live screen or through an
 * in-world embodiment; on this line there is one route — the coremod's keyboard/pointer seam — so the
 * input verbs call {@link KeyState} directly and there is no driver to select. The bind verbs spell
 * their target as a plain LWJGL key code here rather than 1.21.1's {@code options -> options.keyChat}
 * lambda, because this line's binds are the {@code int keyCode} fields of {@code GameSettings} rather
 * than {@code KeyMapping} objects; {@link #openChat()} is the one that reads the live bind so the
 * scenario does not hard-code a key the options file may have remapped. Every verb that is not here
 * (the element-capture verbs, the in-world hold/look verbs, the JSON-target verbs) is absent because
 * its machinery is absent, not because it was stubbed.
 *
 * <p>Step indices are assigned when the steps are read, not when they are added: the runner asks for
 * the body steps and then the teardown steps, and the teardown steps have to be numbered after every
 * body step however the scenario interleaved its calls.
 */
public final class ScenarioBuilder {

    /** The modifier bit for shift; the four bits mirror GLFW's, so a 1.21.1-written scenario keeps its meaning. */
    public static final int modifierShift = 1;

    /** The modifier bit for control. */
    public static final int modifierControl = 2;

    /** The modifier bit for alt. */
    public static final int modifierAlt = 4;

    /** The modifier bit for super/windows/command. */
    public static final int modifierSuper = 8;

    private final List<Draft> steps = new ArrayList<Draft>();
    private final List<Draft> teardownSteps = new ArrayList<Draft>();

    /** The settle the steps declared after {@link #settleMs(int)} inherit; {@code 0} is the run's default. */
    private int pendingSettleMs;

    // region assertions, logging and captures

    /** Logs a line into the step and into the client log. */
    public ScenarioBuilder log(final String line) {
        return add(StepKind.log, line, 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                context.log(line);
            }
        });
    }

    /** Asserts a condition. */
    public ScenarioBuilder check(final String label, final BooleanSupplier predicate) {
        return check(label, predicate, null);
    }

    /** Asserts a condition, with a detail supplier read only when the assertion runs. */
    public ScenarioBuilder check(
            final String label, final BooleanSupplier predicate, final Supplier<String> detail) {
        return add(StepKind.check, label, 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                boolean passed = predicate.getAsBoolean();
                context.check(label, passed, passed || detail == null ? null : detail.get());
            }
        });
    }

    /** Asserts a value read at run time equals the expected one. */
    public ScenarioBuilder checkEquals(final String label, final Object expected, final Supplier<Object> actual) {
        return add(StepKind.check, label, 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                context.checkEquals(label, expected, actual.get());
            }
        });
    }

    /** Takes a whole-framebuffer screenshot under a label. */
    public ScenarioBuilder screenshot(final String label) {
        return add(StepKind.screenshot, "screenshot " + label, 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                context.screenshot(label);
            }
        });
    }

    /** Asserts a labelled capture is on disk and is not a single flat colour. */
    public ScenarioBuilder checkCaptured(final String label) {
        return add(StepKind.checkCaptured, "capture " + label + " is real", 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                context.checkCaptured(label);
            }
        });
    }

    // endregion

    // region the input chain

    /**
     * Types a string, one character event per character.
     *
     * <p>One event per character, not a press/release pair: LWJGL 2 reports typing as an event with
     * {@code getEventKey() == 0} and a non-zero {@code getEventCharacter()}, which is the shape
     * {@code GuiScreen.handleKeyboardInput} tests for. See {@link KeyState#type(String)}.
     *
     * <p><b>Port note (why every input verb here waits for its own events to be consumed).</b> The run's
     * clock is the rendered frame, but the game consumes input on the client <em>tick</em>, and
     * {@code Minecraft.runGameLoop} runs the tick loop {@code min(10, timer.elapsedTicks)} times per
     * frame — so most frames run no tick at all (at 60 fps roughly two frames in three). A step that
     * only queued its events would therefore finish on a frame the game never looked at them, and the
     * next step would assert against a chat box the typing had not reached (measured: the first smoke
     * run read an empty chat box). So a queuing verb queues once and then repeats until
     * {@link KeyState#pending()} is empty — the same "an unfinished step calls repeat" discipline the
     * wait steps use, applied to the seam's own queue.
     */
    public ScenarioBuilder type(final String text) {
        final boolean[] queued = new boolean[1];
        return add(StepKind.type, "type \"" + text + "\"", 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                if (!queued[0]) {
                    queued[0] = true;
                    context.log("typing " + text.length() + " character(s) through the keyboard seam");
                    KeyState.type(text);
                }
                if (!KeyState.pending().isEmpty()) {
                    context.repeat("the game consumed the queued characters");
                }
            }
        });
    }

    /**
     * Opens the chat box by pressing the chat bind, through the game's own bind processing.
     *
     * <p>This is the 1.12.2 spelling of the 1.21.1 smoke's {@code pressBind(options -> options.keyChat)}.
     * The key is read from the live {@code GameSettings} bind, and the press is a queue of a down and
     * an up event answered by the seam — the same two events a real keyboard produces, from which
     * {@code Minecraft.runTickKeyboard} calls {@code KeyBinding.onTick} and
     * {@code Minecraft.processKeyBinds} opens the screen. Nothing here opens the screen itself: if the
     * bind chain is broken, no chat box appears and the following {@code awaitScreen} says so. It waits
     * for its own events to be consumed for the reason {@link #type(String)} gives.
     */
    public ScenarioBuilder openChat() {
        final boolean[] queued = new boolean[1];
        return add(StepKind.pressBind, "press the chat bind", 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                if (!queued[0]) {
                    queued[0] = true;
                    Minecraft minecraft = Minecraft.getMinecraft();
                    int key = minecraft.gameSettings.keyBindChat.getKeyCode();
                    context.log("pressing the chat bind (key " + key + ")");
                    // A released tap: the up event is queued in the same frame, so the bind's press is
                    // consumed by one tick's processKeyBinds and the key never stays held.
                    KeyState.tap(key, '\u0000');
                }
                if (!KeyState.pending().isEmpty()) {
                    context.repeat("the game consumed the chat key tap");
                }
            }
        });
    }

    /** Queues a key-down event and marks the key held, so a following wait observes a real hold. */
    public ScenarioBuilder keyDown(final int key) {
        final boolean[] queued = new boolean[1];
        return add(StepKind.keyDown, "key down " + key, 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                if (!queued[0]) {
                    queued[0] = true;
                    KeyState.setDown(key, true);
                    KeyState.enqueue(key, '\u0000', true);
                }
                if (!KeyState.pending().isEmpty()) {
                    context.repeat("the game consumed the key-down event");
                }
            }
        });
    }

    /** Queues a key-up event and clears the hold. */
    public ScenarioBuilder keyUp(final int key) {
        final boolean[] queued = new boolean[1];
        return add(StepKind.keyUp, "key up " + key, 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                if (!queued[0]) {
                    queued[0] = true;
                    KeyState.setDown(key, false);
                    KeyState.enqueue(key, '\u0000', false);
                }
                if (!KeyState.pending().isEmpty()) {
                    context.repeat("the game consumed the key-up event");
                }
            }
        });
    }

    // endregion

    // region screens and time

    /** Waits for the client to show a screen of the given class. */
    public ScenarioBuilder awaitScreen(final Class<? extends GuiScreen> type) {
        return add(StepKind.awaitScreen, "await " + type.getSimpleName(), 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                if (type.isInstance(context.screen())) return;
                context.repeat("screen of type " + type.getSimpleName() + " (now "
                        + nameOf(context.screen()) + ")");
            }
        });
    }

    /** Asserts the client shows a screen of the given class. */
    public ScenarioBuilder checkScreen(final Class<? extends GuiScreen> type) {
        return add(StepKind.checkScreen, "screen is " + type.getSimpleName(), 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                GuiScreen screen = context.screen();
                context.check(
                        "the open screen is " + type.getSimpleName(),
                        type.isInstance(screen),
                        "was " + nameOf(screen));
            }
        });
    }

    /** Waits a number of rendered frames. */
    public ScenarioBuilder waitFrames(final int frames) {
        final int[] seen = new int[1];
        return add(StepKind.waitFrames, "wait " + frames + " frame(s)", 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                if (++seen[0] >= frames) return;
                context.repeat(seen[0] + "/" + frames + " frame(s)");
            }
        });
    }

    /**
     * Waits a number of client ticks.
     *
     * <p>Measured on the world's own tick clock ({@code EntityPlayerSP.ticksExisted}) rather than on
     * frames: the client tick loop runs 0..10 times per rendered frame, so a hold counted in frames
     * would be a different length in seconds on every machine. The integrated server ticks in the same
     * loop as the client on this line, which is why {@link #serverTicks(int)} is the same wait.
     */
    public ScenarioBuilder ticks(final int ticks) {
        final int[] start = new int[1];
        final boolean[] started = new boolean[1];
        return add(StepKind.waitTicks, "wait " + ticks + " tick(s)", 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                int now = playerTicks(context);
                if (!started[0]) {
                    started[0] = true;
                    start[0] = now;
                }
                if (now >= 0 && now - start[0] >= ticks) return;
                context.repeat((now - start[0]) + "/" + ticks + " tick(s)");
            }
        });
    }

    /** The world's tick clock. Same wait as {@link #ticks(int)}: the server ticks with the client here. */
    public ScenarioBuilder serverTicks(int ticks) {
        return ticks(ticks);
    }

    /** Waits until a predicate holds, reporting the reason while it does not. */
    public ScenarioBuilder waitUntil(final String reason, final BooleanSupplier predicate) {
        return add(StepKind.waitUntil, "wait until " + reason, 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                if (predicate.getAsBoolean()) return;
                context.repeat(reason);
            }
        });
    }

    // endregion

    // region the JSON vocabulary's verbs

    /**
     * Sets the settle every step declared after this inherits, in ms; {@code 0} restores the run's.
     *
     * <p>Not a step of its own — it is a property of the steps that follow, exactly as it is on
     * 1.21.1 — so it costs no frame.
     */
    public ScenarioBuilder settleMs(int ms) {
        pendingSettleMs = ms;
        return this;
    }

    /** Holds the clock for a wall-clock time. */
    public ScenarioBuilder waitMs(final int millis) {
        final long[] anchor = new long[1];
        return add(StepKind.waitMs, "wait " + millis + " ms", 0, millis + 5_000, new StepBody() {
            @Override
            public void run(TestContext context) {
                long now = System.currentTimeMillis();
                if (anchor[0] == 0L) anchor[0] = now;
                long remaining = anchor[0] + millis - now;
                if (remaining > 0L) context.repeat("waiting " + remaining + " ms");
            }
        });
    }

    /** Holds the clock a number of rendered frames. The JSON vocabulary's name for {@link #waitFrames(int)}. */
    public ScenarioBuilder frames(int frames) {
        return waitFrames(frames);
    }

    /** Closes whatever screen is shown. */
    public ScenarioBuilder closeScreen() {
        return add(StepKind.closeScreen, "close the screen", 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                context.minecraft().displayGuiScreen(null);
            }
        });
    }

    /**
     * Presses a binding as a real key tap, with the modifier the binding declares held for real.
     *
     * <p>The modifier is not decoration: a Forge bind's {@code isActiveAndMatches} is answered by
     * {@code KeyModifier.isActive()}, which asks the game whether the modifier key is down, so a chord
     * without the modifier held is a chord the game never sees. JEI's {@code key.jei.focusSearch} is
     * one: it is Ctrl+F, and pressing F alone does nothing. The hold is its own step either side of
     * the tap, because the tap is consumed on a later frame than the one that queued it — see
     * {@link #type(String)}.
     */
    public ScenarioBuilder pressBind(final KeyBinding binding) {
        int[] modifiers = modifierKeysOf(binding.getKeyModifier());
        for (int modifier : modifiers) {
            keyDown(modifier);
        }
        tap(StepKind.pressBind, "press bind " + binding.getKeyDescription(), binding.getKeyCode());
        for (int i = modifiers.length - 1; i >= 0; i--) {
            keyUp(modifiers[i]);
        }
        return this;
    }

    /**
     * Presses and releases a key, with the modifier mask held as real keys.
     *
     * <p>The mask's bits are {@link #modifierShift} and friends. The {@code press_key} and {@code key}
     * JSON steps differ on 1.21.1 by which listener sees the key (a handler vs the screen); on this
     * line there is one route, the coremod seam, so both spell the same gesture — the two names are
     * kept so a scenario written for the 1.21.1 vocabulary does not have to change.
     */
    public ScenarioBuilder pressKey(int key, int modifiers) {
        return gesture(StepKind.pressKey, "press " + keyName(key), key, modifiers);
    }

    /** Presses and releases a key with modifiers held; see {@link #pressKey(int, int)}. */
    public ScenarioBuilder key(int key, int modifiers) {
        return gesture(StepKind.key, "key " + keyName(key), key, modifiers);
    }

    /**
     * Types a string one character event per character; the 1.21.1 vocabulary's {@code type_text}.
     *
     * <p>Identical to {@link #type(String)} on this line: both go through the keyboard seam's
     * character events, which is the one route to a focused field or a mod overlay's search bar.
     */
    public ScenarioBuilder typeText(String text) {
        return type(text);
    }

    /**
     * Replaces the focused text field's content: Ctrl+A, backspace, then type.
     *
     * <p>{@code GuiScreen.isKeyComboCtrlA} is what a 1.12.2 {@code GuiTextField} reads for select-all
     * (GuiTextField.java:346-351), so this is the real gesture, not a forged field write.
     */
    public ScenarioBuilder typeInto(String text) {
        key(Keyboard.KEY_A, modifierControl);
        key(Keyboard.KEY_BACK, 0);
        type(text);
        return this;
    }

    /** Left clicks a point, in GUI-scaled screen coordinates. */
    public ScenarioBuilder click(double x, double y) {
        return clickAt(x, y, 0);
    }

    /** Right clicks a point. */
    public ScenarioBuilder rightClick(double x, double y) {
        return clickAt(x, y, 1);
    }

    /** Clicks a point with shift held as a real key, because held state is what the game reads. */
    public ScenarioBuilder shiftClick(double x, double y) {
        keyDown(Keyboard.KEY_LSHIFT);
        clickAt(x, y, 0);
        keyUp(Keyboard.KEY_LSHIFT);
        return this;
    }

    /** Clicks a point twice, with the intermediate gap a settle well under a double-click threshold. */
    public ScenarioBuilder doubleClick(double x, double y) {
        clickAt(x, y, 0);
        int saved = pendingSettleMs;
        pendingSettleMs = 30;
        clickAt(x, y, 0);
        pendingSettleMs = saved;
        return this;
    }

    /**
     * Clicks at GUI-scaled coordinates with an explicit button.
     *
     * <p>The point is converted to the display pixels {@code Mouse.getEventX/Y()} carries, because
     * {@code GuiScreen.handleMouseInput} scales those back up itself
     * ({@code x * width / displayWidth}, and the y axis is flipped: GuiScreen.java:598-599). A
     * scenario that computed the conversion would have to know the live screen size; doing it here
     * keeps the vocabulary in GUI space.
     */
    public ScenarioBuilder clickAt(final double x, final double y, final int button) {
        final boolean[] clicked = new boolean[1];
        return add(StepKind.click, "click " + x + "," + y + " button " + button, 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                GuiScreen screen = context.screenOrThrow();
                if (!clicked[0]) {
                    clicked[0] = true;
                    placeCursor(context.minecraft(), screen, x, y);
                    context.log("clicking " + x + "," + y + " at display " + CursorState.x() + ","
                            + CursorState.y() + " (screen " + screen.width + "x" + screen.height + ")");
                    CursorState.click(button);
                }
                if (CursorState.queued() > 0) {
                    context.repeat("the game consumed the click");
                }
            }
        });
    }

    /** Turns the game's config to a pinyin spelling. The JSON vocabulary's {@code record_matches}. */
    public ScenarioBuilder recordMatches(final boolean enable) {
        return action("recordMatches " + enable, context -> {
            MatchRecorder.setEnabled(enable);
            context.log(enable ? "match recorder on" : "match recorder off");
        });
    }

    /** Drops every comparison the recorder holds, so the next assertion sees only what follows. */
    public ScenarioBuilder clearRecordedMatches() {
        return action("clearRecordedMatches", context -> {
            MatchRecorder.clear();
            context.log("match recorder cleared");
        });
    }

    /**
     * Asserts what the match recorder holds.
     *
     * <p>With {@code expect} set the step waits up to {@code withinMs} for a recorded comparison the
     * filter accepts, and fails only when the budget runs out, so a search that lands a few frames
     * after the keystroke is not a false red. With {@code expect} clear the step cannot wait, so it
     * decides on one attempt: failing the moment a recorded comparison does satisfy the filter. A
     * failure reports the tail of what was recorded, because "the engine never saw this query" and
     * "the engine saw it and answered no" are different diagnoses.
     */
    public ScenarioBuilder assertMatchRecorded(
            final MatchFilter filter, final boolean expect, final int withinMs) {
        final int budget = withinMs > 0 ? withinMs : 3_000;
        final long[] anchor = new long[1];
        final String label = "match recorded " + (expect ? "for " : "for none of ") + filter.describe();
        return add(
                StepKind.check,
                "assertMatchRecorded " + (expect ? "" : "not ") + filter.describe(),
                0,
                budget + 5_000,
                new StepBody() {
                    @Override
                    public void run(TestContext context) {
                        long now = System.currentTimeMillis();
                        if (anchor[0] == 0L) anchor[0] = now;
                        MatchRecorder.Record found = MatchRecorder.newestMatching(filter);
                        if (!expect) {
                            context.check(
                                    label,
                                    found == null,
                                    found == null ? "nothing matched" : "but " + found + " was recorded");
                            return;
                        }
                        if (found != null) {
                            context.check(label, true, found.toString());
                            return;
                        }
                        if (now - anchor[0] < budget) {
                            context.repeat("waiting " + budget + " ms for " + filter.describe());
                            return;
                        }
                        context.check(label, false, MatchRecorder.describeMiss(filter));
                    }
                });
    }

    /** Moves the local player to an absolute position, through a server command. */
    public ScenarioBuilder teleportPlayer(final double x, final double y, final double z) {
        return serverCommand(StepKind.teleportPlayer, "teleport " + x + "," + y + "," + z, () -> {
            String name = localPlayerName();
            return name == null ? "/tp " + x + " " + y + " " + z : "/tp " + name + " " + x + " " + y + " " + z;
        });
    }

    /** Puts a stack of an item into the local player's inventory, through a server command. */
    public ScenarioBuilder giveItem(final String item, final int count) {
        return serverCommand(StepKind.giveItem, "give " + item + " x" + count, () -> {
            String name = localPlayerName();
            return name == null ? "/give " + item + " " + count : "/give " + name + " " + item + " " + count;
        });
    }

    /** Runs a server command, bypassing all input. */
    public ScenarioBuilder runCommand(final String command) {
        return serverCommand(StepKind.runCommand, "command " + command, () -> command);
    }

    // endregion

    // region free-form and teardown

    /** A free-form step: what it does is the body's own business. */
    public ScenarioBuilder action(final String name, final Consumer<TestContext> body) {
        return add(StepKind.action, name, 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                body.accept(context);
            }
        });
    }

    /** A step that runs after the scenario is over, whatever its outcome. */
    public ScenarioBuilder teardown(final String name, final Runnable body) {
        teardownSteps.add(new Draft(StepKind.teardown, name, 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                body.run();
            }
        }));
        return this;
    }

    /**
     * A step that runs after the scenario is over, whatever its outcome, named after what it does.
     *
     * <p>The 1.21.1 smoke calls {@code teardown(() -> setScreen(null))}; the name is derived here so
     * the report's row is not blank. A lambda's own name is not readable at run time, so the step is
     * named for the verb — the only teardown shape the Java scenarios have today is a screen reset.
     */
    public ScenarioBuilder teardown(final Runnable body) {
        return teardown("teardown", body);
    }

    /** The body steps, numbered from zero in the order they were added. */
    public List<Step> steps() {
        return materialize(steps, 0);
    }

    /** The client teardown steps, numbered after every body step. */
    public List<Step> teardownSteps() {
        return materialize(teardownSteps, steps.size());
    }

    // endregion

    // region the gesture and server helpers

    /** Queues a tap of a key through the seam and holds the step until the game consumed it. */
    private ScenarioBuilder tap(StepKind kind, String name, final int key) {
        final boolean[] queued = new boolean[1];
        return add(kind, name, 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                if (!queued[0]) {
                    queued[0] = true;
                    KeyState.tap(key, '\u0000');
                }
                if (!KeyState.pending().isEmpty()) {
                    context.repeat("the game consumed the key tap");
                }
            }
        });
    }

    /** Holds the modifier keys, taps the key, then releases them, each as its own step. */
    private ScenarioBuilder gesture(StepKind kind, String name, int key, int modifiers) {
        int[] modifierKeys = modifierKeysOf(modifiers);
        for (int modifier : modifierKeys) {
            keyDown(modifier);
        }
        tap(kind, name, key);
        for (int i = modifierKeys.length - 1; i >= 0; i--) {
            keyUp(modifierKeys[i]);
        }
        return this;
    }

    /** Places the scripted pointer so the game's own scaling maps it back onto the point asked for. */
    private static void placeCursor(Minecraft minecraft, GuiScreen screen, double x, double y) {
        // Inverse of GuiScreen.handleMouseInput: i = eventX * width / displayWidth,
        // j = height - eventY * height / displayHeight - 1 (GuiScreen.java:598-599).
        int px = (int) Math.round(x * minecraft.displayWidth / (double) screen.width);
        int py = (int) Math.round((screen.height - y - 1) * minecraft.displayHeight / (double) screen.height);
        CursorState.moveTo(px, py);
    }

    /**
     * Submits a command to the integrated server and holds the step until the server thread ran it.
     *
     * <p>{@code MinecraftServer.addScheduledTask} runs the body immediately when called from the
     * server thread and queues it otherwise (MinecraftServer.java:1520-1550), so the client thread
     * submits and waits. The sender is the server's own player when there is one, which is what makes
     * {@code @p} and relative coordinates mean what a player typing the command expects; with no
     * player the console sender is used. {@code localPlayerName} is read at run time, not define
     * time, so the command names the player the run actually has.
     */
    private ScenarioBuilder serverCommand(StepKind kind, final String name, final Supplier<String> command) {
        final boolean[] submitted = new boolean[1];
        final AtomicBoolean ran = new AtomicBoolean();
        return add(kind, name, 0, 15_000, new StepBody() {
            @Override
            public void run(TestContext context) {
                final MinecraftServer server = Minecraft.getMinecraft().getIntegratedServer();
                if (server == null) {
                    context.repeat("waiting for the integrated server");
                    return;
                }
                if (!submitted[0]) {
                    submitted[0] = true;
                    final String text = stripLeadingSlash(command.get());
                    context.log("submitting server command /" + text);
                    server.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                ICommandSender sender = server;
                                List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
                                if (!players.isEmpty()) sender = players.get(0);
                                server.getCommandManager().executeCommand(sender, text);
                            } finally {
                                ran.set(true);
                            }
                        }
                    });
                }
                if (!ran.get()) {
                    context.repeat("the server runs " + name);
                }
            }
        });
    }

    /** The local player's name, or {@code null} before one exists. */
    private static String localPlayerName() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft.player == null ? null : minecraft.player.getName();
    }

    private static String stripLeadingSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    /** The LWJGL keys a GLFW-style modifier mask names. */
    private static int[] modifierKeysOf(int modifiers) {
        List<Integer> keys = new ArrayList<Integer>();
        if ((modifiers & modifierShift) != 0) keys.add(Keyboard.KEY_LSHIFT);
        if ((modifiers & modifierControl) != 0) keys.add(controlKey());
        if ((modifiers & modifierAlt) != 0) keys.add(Keyboard.KEY_LMENU);
        if ((modifiers & modifierSuper) != 0) keys.add(Keyboard.KEY_LWIN);
        int[] result = new int[keys.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = keys.get(i);
        }
        return result;
    }

    /** The LWJGL keys a Forge bind's own modifier names. */
    private static int[] modifierKeysOf(KeyModifier modifier) {
        if (modifier == null || modifier == KeyModifier.NONE) return new int[0];
        if (modifier == KeyModifier.SHIFT) return new int[]{Keyboard.KEY_LSHIFT};
        if (modifier == KeyModifier.CONTROL) return new int[]{controlKey()};
        if (modifier == KeyModifier.ALT) return new int[]{Keyboard.KEY_LMENU};
        return new int[0];
    }

    /**
     * The key the game calls Ctrl.
     *
     * <p>Not always {@code KEY_LCONTROL}: the game's own notion of "the ctrl key is down" is
     * {@code GuiScreen.isCtrlKeyDown()}, which on macOS asks for the meta keys 219/220 — the Command
     * key (GuiScreen.java:757-770). A Forge bind's CONTROL modifier is answered by exactly that method
     * ({@code KeyModifier.CONTROL.isActive()}), so holding the physical Ctrl key for a Ctrl+F bind
     * would be a chord the game never sees on a Mac.
     */
    private static int controlKey() {
        return Minecraft.IS_RUNNING_ON_MAC ? Keyboard.KEY_LWIN : Keyboard.KEY_LCONTROL;
    }

    private static String keyName(int keyCode) {
        String name = Keyboard.getKeyName(keyCode);
        return name == null ? Integer.toString(keyCode) : name;
    }

    // endregion

    private ScenarioBuilder add(
            StepKind kind, String name, int settleMs, int timeoutMs, StepBody body) {
        // A step that names no settle of its own inherits the one a preceding settleMs() set. The
        // default is 0, which the runner reads as "use the run's settle", so an ordinary scenario is
        // unaffected.
        int effectiveSettle = settleMs != 0 ? settleMs : pendingSettleMs;
        steps.add(new Draft(kind, name, effectiveSettle, timeoutMs, body));
        return this;
    }

    private static List<Step> materialize(List<Draft> drafts, int firstIndex) {
        List<Step> built = new ArrayList<Step>(drafts.size());
        for (int i = 0; i < drafts.size(); i++) {
            Draft draft = drafts.get(i);
            built.add(new Step(
                    firstIndex + i,
                    draft.kind,
                    draft.name,
                    null,
                    draft.settleMs,
                    draft.timeoutMs,
                    draft.body));
        }
        return Collections.unmodifiableList(built);
    }

    private static int playerTicks(TestContext context) {
        Minecraft minecraft = context.minecraft();
        return minecraft.player == null ? -1 : minecraft.player.ticksExisted;
    }

    private static String nameOf(GuiScreen screen) {
        return screen == null ? "no screen" : screen.getClass().getSimpleName();
    }

    /** One step before its index is known. */
    private static final class Draft {

        private final StepKind kind;
        private final String name;
        private final int settleMs;
        private final int timeoutMs;
        private final StepBody body;

        Draft(StepKind kind, String name, int settleMs, int timeoutMs, StepBody body) {
            this.kind = kind;
            this.name = name;
            this.settleMs = settleMs;
            this.timeoutMs = timeoutMs;
            this.body = body;
        }
    }
}
