package me.towdium.jecharacters.uitest.datadriven;

import me.towdium.jecharacters.uitest.api.ScenarioBuilder;
import me.towdium.jecharacters.uitest.input.Keys;
import me.towdium.jecharacters.uitest.match.MatchFilter;
import me.towdium.jecharacters.uitest.platform.ClientPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ScenarioSteps {

    @FunctionalInterface
    private interface Handler {

        void apply(ScenarioBuilder builder, Args args, String context);
    }

    private static final Map<String, Handler> handlers = new LinkedHashMap<>();

    static {
        register("log", (builder, args, context) -> builder.log(args.string("text")));
        register("settle", (builder, args, context) -> builder.settleMs(args.integer("ms")));
        register("wait_ms", (builder, args, context) -> builder.waitMs(args.integer("ms")));
        register("wait_frames", (builder, args, context) -> builder.frames(args.integer("frames")));

        register("open_chat", (builder, args, context) -> {
            builder.pressBind(options -> options.keyChat);
            builder.awaitScreen(ChatScreen.class);
        });

        register("close_screen", (builder, args, context) -> builder.closeScreen());

        register("wait_until_screen", (builder, args, context) -> {
            if (!args.has("screen")) {
                throw new ScenarioJsonException(
                    context + ": 'screen' is required; use null to wait for no screen to be open"
                );
            }
            String name = args.stringOrNull("screen");
            if (name == null) {
                builder.waitUntil("no screen is open", () -> ClientPlatform.current().screen() == null);
                return;
            }
            Class<? extends Screen> type = JsonTargets.screenClass(name);
            builder.waitUntil(
                "the " + type.getSimpleName() + " screen is open",
                () -> type.isInstance(ClientPlatform.current().screen())
            );
            builder.checkScreen(type);
        });

        register("press_bind", (builder, args, context) ->
            builder.pressBind(JsonTargets.bind(args.string("bind"))));

        register("press_key", (builder, args, context) -> {
            int key = JsonTargets.key(args.string("key"));
            int mask = JsonTargets.modifierMask(args.stringsOrEmpty("modifiers"));
            for (int i = 0; i < times(args, context); i++) {
                builder.pressKey(key, mask);
            }
        });

        register("key", (builder, args, context) -> {
            int key = JsonTargets.key(args.string("key"));
            int mask = JsonTargets.modifierMask(args.stringsOrEmpty("modifiers"));
            for (int i = 0; i < times(args, context); i++) {
                builder.key(key, mask);
            }
        });

        register("enter", (builder, args, context) -> builder.key(Keys.enter, 0));

        register("type", (builder, args, context) -> builder.type(args.string("text")));
        register("type_text", (builder, args, context) -> builder.typeText(args.string("text")));
        register("type_into", (builder, args, context) -> builder.typeInto(args.string("text")));

        register("ime_compose", (builder, args, context) -> {
            String preedit = args.string("text");
            builder.action(
                "ime compose " + preedit,
                step -> step.driver().imeCompose(preedit)
            );
        });

        register("ime_commit", (builder, args, context) -> {
            String committed = args.string("text");
            builder.action(
                "ime commit " + committed,
                step -> step.driver().imeCommit(committed)
            );
        });

        register("assert_focus_text", (builder, args, context) -> {
            if (!args.has("text")) {
                throw new ScenarioJsonException(
                    context + ": 'text' is required; use null to assert that the focused widget is empty"
                );
            }
            String named = args.stringOrNull("text");
            String expected = named == null ? "" : named;
            builder.action(
                "assertFocusText " + expected,
                step -> step.checkEquals(
                    "the focused widget's text", expected, step.driver().imeFocusedText()
                )
            );
        });

        register("assert_focus_preedit", (builder, args, context) -> {
            boolean expect = args.bool("expect");
            builder.action(
                "assertFocusPreedit " + expect,
                step -> step.check(
                    "the focused widget carries an IME pre-edit overlay",
                    step.driver().imeFocusedPreeditOverlay() == expect,
                    "expected " + expect
                )
            );
        });

        register("click", (builder, args, context) -> {
            double x = args.number("x");
            double y = args.number("y");
            String button = args.stringOr("button", "left").toLowerCase(Locale.ROOT);
            boolean shift = args.boolOr("shift", false);
            if (args.boolOr("double", false)) {
                builder.doubleClick(x, y);
                return;
            }
            switch (button) {
                case "left" -> {
                    if (shift) {
                        builder.shiftClick(x, y);
                    } else {
                        builder.click(x, y);
                    }
                }
                case "right" -> builder.rightClick(x, y);
                case "middle" -> builder.clickAt(x, y, 2);
                default -> throw new ScenarioJsonException(
                    context + ": button '" + button + "' is not left, right or middle"
                );
            }
        });

        register("command", (builder, args, context) -> {
            String text = args.string("text");
            builder.pressBind(options -> options.keyChat);
            builder.awaitScreen(ChatScreen.class);
            builder.type(text);
            builder.key(Keys.enter, 0);
            builder.waitUntil("the chat screen closes", () -> ClientPlatform.current().screen() == null);
        });

        register("record_matches", (builder, args, context) -> {
            boolean enable = args.boolOr("enable", false);
            boolean clear = args.boolOr("clear", false);
            boolean named = args.has("enable") || args.has("clear");
            if (!named) {
                throw new ScenarioJsonException(
                    context + ": name 'enable' and/or 'clear'; a step that does neither is a no-op"
                );
            }
            if (args.has("enable")) {
                builder.recordMatches(enable);
            }
            if (clear) {
                builder.clearRecordedMatches();
            }
        });

        register("assert_match_recorded", (builder, args, context) -> {
            MatchFilter filter = new MatchFilter(
                args.stringOrNull("method"),
                args.stringOrNull("query_contains"),
                args.stringOrNull("candidate_contains")
            );
            if (filter.isEmpty()) {
                throw new ScenarioJsonException(
                    context + ": name at least one of 'method', 'query_contains', 'candidate_contains'"
                );
            }
            boolean expect = args.boolOr("expect", true);
            builder.assertMatchRecorded(filter, expect, args.integerOr("within_ms", 0));
        });

        register("screenshot", (builder, args, context) -> builder.screenshot(args.string("label")));

        register("teleport", (builder, args, context) ->
            builder.teleportPlayer(args.number("x"), args.number("y"), args.number("z")));

        register("give", (builder, args, context) -> {
            String item = args.string("item");
            int count = args.integerOr("count", 1);
            try {
                builder.giveItem(item, count);
            } catch (RuntimeException e) {
                throw new ScenarioJsonException(context + ": '" + item + "' is not an item id", e);
            }
        });

        register("run_server_command", (builder, args, context) -> builder.runCommand(args.string("text")));
    }

    private ScenarioSteps() {}

    static void apply(ScenarioBuilder builder, JsonStep step) {
        Handler handler = handlers.get(step.action());
        if (handler == null) {
            throw new ScenarioJsonException(
                step.describe() + ": '" + step.action() + "' is not a step; known steps: "
                        + describeVocabulary()
            );
        }
        String context = step.describe();
        Args args = new Args(context, step.args());
        handler.apply(builder, args, context);
        args.done();
    }

    static boolean isKnown(String action) {
        return handlers.containsKey(action);
    }

    static String describeVocabulary() {
        return String.join(", ", handlers.keySet());
    }

    static List<String> names() {
        return List.copyOf(handlers.keySet());
    }

    private static int times(Args args, String context) {
        int times = args.integerOr("times", 1);
        if (times < 1 || times > 64) {
            throw new ScenarioJsonException(context + ": 'times' must be between 1 and 64, got " + times);
        }
        return times;
    }

    private static void register(String name, Handler handler) {
        handlers.put(name, handler);
    }
}
