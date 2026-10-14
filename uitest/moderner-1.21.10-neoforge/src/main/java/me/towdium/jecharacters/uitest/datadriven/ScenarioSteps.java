package me.towdium.jecharacters.uitest.datadriven;

import me.towdium.jecharacters.uitest.api.ScenarioBuilder;
import me.towdium.jecharacters.uitest.match.MatchFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The step vocabulary: which step names a JSON scenario may use, and what each one does.
 *
 * <p>One step name, one handler, and the handler is nothing but a guarded call into
 * {@link ScenarioBuilder} — the same verbs the Java scenarios use, so the two layers cannot drift
 * into meaning different things. Adding a step is adding a line here; a step name that is not here is
 * refused while the scenario is read, naming the name and the whole vocabulary, which is what keeps a
 * typo from becoming a no-op.
 *
 * <p>Every handler reads all of its parameters through {@link Args}, so a key that is missing, of the
 * wrong type, or not a parameter of this step at all fails with a message naming the step and the
 * key. There is deliberately no "ignore what you do not understand" path.
 *
 * <p>The vocabulary is documented for authors in
 * {@code src/uitest/resources/uitest/scenarios/README.md}.
 */
final class ScenarioSteps {

    /** What a step does to a scenario. */
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
                builder.waitUntil("no screen is open", () -> Minecraft.getInstance().screen == null);
                return;
            }
            Class<? extends Screen> type = JsonTargets.screenClass(name);
            builder.waitUntil(
                "the " + type.getSimpleName() + " screen is open",
                () -> type.isInstance(Minecraft.getInstance().screen)
            );
            builder.checkScreen(type);
        });

        register("press_bind", (builder, args, context) ->
            builder.pressBind(JsonTargets.bind(args.string("bind"))));

        register("press_key", (builder, args, context) -> {
            int key = JsonTargets.glfwKey(args.string("key"));
            int mask = JsonTargets.modifierMask(args.stringsOrEmpty("modifiers"));
            for (int i = 0; i < times(args, context); i++) {
                builder.pressKey(key, mask);
            }
        });

        register("key", (builder, args, context) -> {
            int key = JsonTargets.glfwKey(args.string("key"));
            int mask = JsonTargets.modifierMask(args.stringsOrEmpty("modifiers"));
            for (int i = 0; i < times(args, context); i++) {
                builder.key(key, mask);
            }
        });

        register("enter", (builder, args, context) -> builder.key(GLFW.GLFW_KEY_ENTER, 0));

        register("type", (builder, args, context) -> builder.type(args.string("text")));
        register("type_text", (builder, args, context) -> builder.typeText(args.string("text")));
        register("type_into", (builder, args, context) -> builder.typeInto(args.string("text")));

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
            builder.key(GLFW.GLFW_KEY_ENTER, 0);
            builder.waitUntil("the chat screen closes", () -> Minecraft.getInstance().screen == null);
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
            ResourceLocation location;
            try {
                location = ResourceLocation.parse(item);
            } catch (RuntimeException e) {
                throw new ScenarioJsonException(context + ": '" + item + "' is not an item id", e);
            }
            builder.giveItem(location, args.integerOr("count", 1));
        });

        register("run_server_command", (builder, args, context) -> builder.runCommand(args.string("text")));
    }

    private ScenarioSteps() {}

    /** Runs one declared step, or throws naming it. */
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

    /** Whether a step name is part of the vocabulary. */
    static boolean isKnown(String action) {
        return handlers.containsKey(action);
    }

    /** The whole vocabulary, for a message or a log line. */
    static String describeVocabulary() {
        return String.join(", ", handlers.keySet());
    }

    /** The step names, in declaration order. */
    static List<String> names() {
        return List.copyOf(handlers.keySet());
    }

    /**
     * How many times a gesture step runs.
     *
     * <p>Some gestures are one verb repeated rather than a verb of their own — pressing backspace
     * four times to clear a field, for instance — so the repeat is a parameter instead of a step name
     * per count.
     */
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
