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

public final class ScenarioBuilder {

    public static final int modifierShift = 1;

    public static final int modifierControl = 2;

    public static final int modifierAlt = 4;

    public static final int modifierSuper = 8;

    private final List<Draft> steps = new ArrayList<Draft>();
    private final List<Draft> teardownSteps = new ArrayList<Draft>();

    private int pendingSettleMs;

    // region assertions, logging and captures

    public ScenarioBuilder log(final String line) {
        return add(StepKind.log, line, 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                context.log(line);
            }
        });
    }

    public ScenarioBuilder check(final String label, final BooleanSupplier predicate) {
        return check(label, predicate, null);
    }

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

    public ScenarioBuilder checkEquals(final String label, final Object expected, final Supplier<Object> actual) {
        return add(StepKind.check, label, 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                context.checkEquals(label, expected, actual.get());
            }
        });
    }

    public ScenarioBuilder screenshot(final String label) {
        return add(StepKind.screenshot, "screenshot " + label, 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                context.screenshot(label);
            }
        });
    }

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
                    KeyState.tap(key, '\u0000');
                }
                if (!KeyState.pending().isEmpty()) {
                    context.repeat("the game consumed the chat key tap");
                }
            }
        });
    }

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

    public ScenarioBuilder serverTicks(int ticks) {
        return ticks(ticks);
    }

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

    public ScenarioBuilder settleMs(int ms) {
        pendingSettleMs = ms;
        return this;
    }

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

    public ScenarioBuilder frames(int frames) {
        return waitFrames(frames);
    }

    public ScenarioBuilder closeScreen() {
        return add(StepKind.closeScreen, "close the screen", 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                context.minecraft().displayGuiScreen(null);
            }
        });
    }

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

    public ScenarioBuilder pressKey(int key, int modifiers) {
        return gesture(StepKind.pressKey, "press " + keyName(key), key, modifiers);
    }

    public ScenarioBuilder key(int key, int modifiers) {
        return gesture(StepKind.key, "key " + keyName(key), key, modifiers);
    }

    public ScenarioBuilder typeText(String text) {
        return type(text);
    }

    public ScenarioBuilder typeInto(String text) {
        key(Keyboard.KEY_A, modifierControl);
        key(Keyboard.KEY_BACK, 0);
        type(text);
        return this;
    }

    public ScenarioBuilder click(double x, double y) {
        return clickAt(x, y, 0);
    }

    public ScenarioBuilder rightClick(double x, double y) {
        return clickAt(x, y, 1);
    }

    public ScenarioBuilder shiftClick(double x, double y) {
        keyDown(Keyboard.KEY_LSHIFT);
        clickAt(x, y, 0);
        keyUp(Keyboard.KEY_LSHIFT);
        return this;
    }

    public ScenarioBuilder doubleClick(double x, double y) {
        clickAt(x, y, 0);
        int saved = pendingSettleMs;
        pendingSettleMs = 30;
        clickAt(x, y, 0);
        pendingSettleMs = saved;
        return this;
    }

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

    public ScenarioBuilder recordMatches(final boolean enable) {
        return action("recordMatches " + enable, context -> {
            MatchRecorder.setEnabled(enable);
            context.log(enable ? "match recorder on" : "match recorder off");
        });
    }

    public ScenarioBuilder clearRecordedMatches() {
        return action("clearRecordedMatches", context -> {
            MatchRecorder.clear();
            context.log("match recorder cleared");
        });
    }

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

    public ScenarioBuilder teleportPlayer(final double x, final double y, final double z) {
        return serverCommand(StepKind.teleportPlayer, "teleport " + x + "," + y + "," + z, () -> {
            String name = localPlayerName();
            return name == null ? "/tp " + x + " " + y + " " + z : "/tp " + name + " " + x + " " + y + " " + z;
        });
    }

    public ScenarioBuilder giveItem(final String item, final int count) {
        return serverCommand(StepKind.giveItem, "give " + item + " x" + count, () -> {
            String name = localPlayerName();
            return name == null ? "/give " + item + " " + count : "/give " + name + " " + item + " " + count;
        });
    }

    public ScenarioBuilder runCommand(final String command) {
        return serverCommand(StepKind.runCommand, "command " + command, () -> command);
    }

    // endregion

    // region free-form and teardown

    public ScenarioBuilder action(final String name, final Consumer<TestContext> body) {
        return add(StepKind.action, name, 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                body.accept(context);
            }
        });
    }

    public ScenarioBuilder teardown(final String name, final Runnable body) {
        teardownSteps.add(new Draft(StepKind.teardown, name, 0, 0, new StepBody() {
            @Override
            public void run(TestContext context) {
                body.run();
            }
        }));
        return this;
    }

    public ScenarioBuilder teardown(final Runnable body) {
        return teardown("teardown", body);
    }

    public List<Step> steps() {
        return materialize(steps, 0);
    }

    public List<Step> teardownSteps() {
        return materialize(teardownSteps, steps.size());
    }

    // endregion

    // region the gesture and server helpers

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

    private static void placeCursor(Minecraft minecraft, GuiScreen screen, double x, double y) {
        int px = (int) Math.round(x * minecraft.displayWidth / (double) screen.width);
        int py = (int) Math.round((screen.height - y - 1) * minecraft.displayHeight / (double) screen.height);
        CursorState.moveTo(px, py);
    }

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

    private static String localPlayerName() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft.player == null ? null : minecraft.player.getName();
    }

    private static String stripLeadingSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

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

    private static int[] modifierKeysOf(KeyModifier modifier) {
        if (modifier == null || modifier == KeyModifier.NONE) return new int[0];
        if (modifier == KeyModifier.SHIFT) return new int[]{Keyboard.KEY_LSHIFT};
        if (modifier == KeyModifier.CONTROL) return new int[]{controlKey()};
        if (modifier == KeyModifier.ALT) return new int[]{Keyboard.KEY_LMENU};
        return new int[0];
    }

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
