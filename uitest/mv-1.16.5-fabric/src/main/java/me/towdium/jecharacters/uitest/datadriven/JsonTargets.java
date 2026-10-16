package me.towdium.jecharacters.uitest.datadriven;

import me.towdium.jecharacters.mixin.uitest.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Turning the names a JSON step uses into the things the engine presses.
 *
 * <p>Three kinds of name are resolved here, all of them at define time so a bad name is a reported
 * failure rather than a step that quietly does nothing:
 *
 * <ul>
 *   <li><b>a GLFW key</b> — {@code "F"}, {@code "ESCAPE"}, {@code "LEFT_CONTROL"} — read off
 *       {@link GLFW}'s own {@code GLFW_KEY_*} constants, so the vocabulary is the platform's and
 *       every key GLFW names is available;</li>
 *   <li><b>a modifier mask</b> — {@code "CTRL"}, {@code "SHIFT"}, {@code "ALT"}, {@code "SUPER"} —
 *       mapped to the GLFW modifier bits a real keystroke would carry;</li>
 *   <li><b>a key bind</b> — either a friendly name for one of vanilla's own ({@code "inventory"},
 *       {@code "chat"}, …) or the mapping's own name as the game spells it
 *       ({@code "key.jei.focusSearch"}), looked up in the registry every registered mapping lives in.
 *       The second form is what lets a scenario drive a mod's bind without the harness knowing the
 *       mod.</li>
 * </ul>
 */
final class JsonTargets {

    private JsonTargets() {}

    /** The GLFW key code a name stands for; {@code "key.keyboard.f"} and {@code "F"} both work. */
    static int glfwKey(String name) {
        String bare = name.trim();
        if (bare.startsWith("key.keyboard.")) {
            bare = bare.substring("key.keyboard.".length());
        }
        String constant = "GLFW_KEY_" + bare.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
        try {
            Field field = GLFW.class.getField(constant);
            Object value = field.get(null);
            if (value instanceof Integer && (Integer) value != GLFW.GLFW_KEY_UNKNOWN) {
                return (Integer) value;
            }
            throw new ScenarioJsonException("key '" + name + "' resolves to GLFW_KEY_UNKNOWN");
        } catch (NoSuchFieldException e) {
            throw new ScenarioJsonException(
                "key '" + name + "' is not a GLFW key name (expected e.g. 'f', 'escape', 'left_control')", e
            );
        } catch (IllegalAccessException e) {
            throw new ScenarioJsonException("key '" + name + "' could not be read", e);
        }
    }

    /** The GLFW modifier mask a list of modifier names contributes. */
    static int modifierMask(List<String> names) {
        int mask = 0;
        for (String name : names) {
            // Port note (1.21.1 → 1.16.5, Java 8): the arrow switch expression is Java 14+.
            mask |= modifierBits(name);
        }
        return mask;
    }

    private static int modifierBits(String name) {
        switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "CTRL":
            case "CONTROL":
                return GLFW.GLFW_MOD_CONTROL;
            case "SHIFT":
                return GLFW.GLFW_MOD_SHIFT;
            case "ALT":
                return GLFW.GLFW_MOD_ALT;
            case "SUPER":
            case "META":
            case "CMD":
            case "COMMAND":
                return GLFW.GLFW_MOD_SUPER;
            default:
                throw new ScenarioJsonException(
                    "modifier '" + name + "' is not one of ctrl, shift, alt, super"
                );
        }
    }

    /** The bind a name stands for, as the options-shaped lookup the builder takes. */
    static Function<Options, KeyMapping> bind(String name) {
        Function<Options, KeyMapping> vanilla = vanillaBind(name.trim().toLowerCase(Locale.ROOT));
        if (vanilla != null) {
            return vanilla;
        }
        return options -> requireMapping(name);
    }

    private static KeyMapping requireMapping(String name) {
        Map<String, KeyMapping> all = KeyMappingAccessor.jechtestAll();
        KeyMapping mapping = all.get(name);
        if (mapping == null) {
            List<String> candidates = new ArrayList<>();
            for (String key : all.keySet()) {
                if (key.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
                    candidates.add(key);
                }
            }
            candidates.sort(String::compareTo);
            throw new ScenarioJsonException(
                "no key bind named '" + name + "' is registered"
                        + (candidates.isEmpty() ? "" : "; did you mean " + candidates + "?")
            );
        }
        return mapping;
    }

    /** A friendly name for one of vanilla's own binds, or {@code null} for anything else. */
    private static Function<Options, KeyMapping> vanillaBind(String name) {
        // Port note (1.21.1 → 1.16.5, Java 8): the arrow switch expression is Java 14+.
        switch (name) {
            case "chat":
                return options -> options.keyChat;
            case "command":
                return options -> options.keyCommand;
            case "inventory":
                return options -> options.keyInventory;
            case "forward":
                return options -> options.keyUp;
            case "back":
                return options -> options.keyDown;
            case "left":
                return options -> options.keyLeft;
            case "right":
                return options -> options.keyRight;
            case "jump":
                return options -> options.keyJump;
            case "sneak":
                return options -> options.keyShift;
            case "sprint":
                return options -> options.keySprint;
            case "drop":
                return options -> options.keyDrop;
            case "attack":
                return options -> options.keyAttack;
            case "use":
                return options -> options.keyUse;
            case "pick":
                return options -> options.keyPickItem;
            case "swap":
                return options -> options.keySwapOffhand;
            case "screenshot":
                return options -> options.keyScreenshot;
            case "fullscreen":
                return options -> options.keyFullscreen;
            case "perspective":
                return options -> options.keyTogglePerspective;
            case "playerlist":
                return options -> options.keyPlayerList;
            case "advancements":
                return options -> options.keyAdvancements;
            default:
                return hotbar(name);
        }
    }

    /** {@code "hotbar.1"} … {@code "hotbar.9"}. */
    private static Function<Options, KeyMapping> hotbar(String name) {
        if (!name.startsWith("hotbar.")) return null;
        int slot;
        try {
            slot = Integer.parseInt(name.substring("hotbar.".length()));
        } catch (NumberFormatException e) {
            return null;
        }
        if (slot < 1 || slot > 9) {
            throw new ScenarioJsonException("hotbar slot " + slot + " is outside 1..9");
        }
        int index = slot - 1;
        return options -> options.keyHotbarSlots[index];
    }

    /**
     * The screen class a name stands for.
     *
     * <p>A fully qualified name is taken as it is; a bare one is looked for in the two packages the
     * game's own screens live in, which is what lets a scenario say {@code "InventoryScreen"}.
     *
     * <p>Scenarios name screens in the game's own (Mojang) names, and those names only exist in a
     * run the loader leaves in that namespace. This harness is loaded as a *mod*, from a jar
     * {@code remapJar} already translated, so a name found by reflection answers for a mod's own
     * screen and for anything spelled out under a name the run really has — but not for the game's
     * own screens, whose names the loader rewrites (see {@link #vanillaScreens}). The game's own
     * screens therefore come from the table below, which is a compile-time reference the remapper
     * translates with the rest of this jar; reflection is only the path for everything the table
     * cannot know.
     */
    static Class<? extends Screen> screenClass(String name) {
        String bare = name.trim();
        Class<? extends Screen> known = vanillaScreens.get(bare);
        if (known != null) {
            return known;
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(bare);
        candidates.add("net.minecraft.client.gui.screens." + bare);
        candidates.add("net.minecraft.client.gui.screens.inventory." + bare);
        for (String candidate : candidates) {
            try {
                Class<?> type = Class.forName(candidate);
                if (Screen.class.isAssignableFrom(type)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Screen> screen = (Class<? extends Screen>) type;
                    return screen;
                }
                throw new ScenarioJsonException(candidate + " is not a Screen");
            } catch (ClassNotFoundException ignored) {
                // try the next spelling / package
            }
        }
        throw new ScenarioJsonException(
            "no screen class named '" + name + "' (try a fully qualified name)"
        );
    }

    /**
     * The game's own screens a scenario may name, as compile-time class references.
     *
     * <p>A client loaded as a mod does not run the game in the namespace a scenario writes. In
     * production the loader targets <b>intermediary</b>: this row's fabric-loader 0.14.9 profile
     * declares {@code net.fabricmc:intermediary:1.16.5} as the mapping tree of the run, and
     * {@code Knot.getTargetNamespace()} is {@code IS_DEVELOPMENT ? "named" : "intermediary"}, so
     * there is no {@code named} namespace in such a run at all and no string can name the game's own
     * classes: measured on this harness's own {@code remapJar} output
     * ({@code build/libs/jechtest-1.16.5-fabric-1.0.0.jar}), whose constant pool already references
     * {@code net/minecraft/class_437} for {@link Screen} and {@code net/minecraft/class_408} for
     * {@link ChatScreen}. A class <i>literal</i> is the reference that does work: the remapper
     * rewrites it here, in this jar, and hands the runtime the class the runtime actually has — the
     * same road {@code ChatScreen.class} in {@link ScenarioSteps} and {@code SmokeScenario} already
     * takes.
     *
     * <p>The key is written out rather than read back off the class: at runtime the class object is
     * the loader's own name for the screen — the entry below comes back as {@code class_408} in the
     * jar this harness ships — so a key derived from the class ({@code type.getName()},
     * {@code type.getSimpleName()}) would not be the name a scenario writes. That is also why a
     * screen this table does not list cannot be addressed in production by a Mojang name: add the
     * line. A mod's own screens need no entry — a fabric mod's classes keep the names their author
     * gave them, so the reflection path above reaches a mod's own screen by fully qualified name even
     * in production.
     *
     * <p>One entry, and that is all this row needs: its selection is the smoke tag, whose scenarios
     * name exactly one screen — {@code json-smoke}'s {@code wait_until_screen} asks for the chat
     * screen, and the Java {@code smoke} scenario spells the same class as a literal. No other name
     * is resolved by reflection anywhere on this row's smoke path.
     */
    private static final Map<String, Class<? extends Screen>> vanillaScreens = vanillaScreens();

    private static Map<String, Class<? extends Screen>> vanillaScreens() {
        // Java 8 (this line's toolchain): Map.copyOf is Java 10, so the immutable wrapper is
        // Collections.unmodifiableMap here.
        Map<String, Class<? extends Screen>> screens = new LinkedHashMap<>();
        screens.put("ChatScreen", ChatScreen.class);
        return Collections.unmodifiableMap(screens);
    }
}
