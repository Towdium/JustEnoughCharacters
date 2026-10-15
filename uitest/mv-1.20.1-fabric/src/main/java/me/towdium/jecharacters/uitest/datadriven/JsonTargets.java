package me.towdium.jecharacters.uitest.datadriven;

import me.towdium.jecharacters.mixin.uitest.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
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
            if (value instanceof Integer code && code != GLFW.GLFW_KEY_UNKNOWN) {
                return code;
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
            mask |= switch (name.trim().toUpperCase(Locale.ROOT)) {
                case "CTRL", "CONTROL" -> GLFW.GLFW_MOD_CONTROL;
                case "SHIFT" -> GLFW.GLFW_MOD_SHIFT;
                case "ALT" -> GLFW.GLFW_MOD_ALT;
                case "SUPER", "META", "CMD", "COMMAND" -> GLFW.GLFW_MOD_SUPER;
                default -> throw new ScenarioJsonException(
                    "modifier '" + name + "' is not one of ctrl, shift, alt, super"
                );
            };
        }
        return mask;
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
        return switch (name) {
            case "chat" -> options -> options.keyChat;
            case "command" -> options -> options.keyCommand;
            case "inventory" -> options -> options.keyInventory;
            case "forward" -> options -> options.keyUp;
            case "back" -> options -> options.keyDown;
            case "left" -> options -> options.keyLeft;
            case "right" -> options -> options.keyRight;
            case "jump" -> options -> options.keyJump;
            case "sneak" -> options -> options.keyShift;
            case "sprint" -> options -> options.keySprint;
            case "drop" -> options -> options.keyDrop;
            case "attack" -> options -> options.keyAttack;
            case "use" -> options -> options.keyUse;
            case "pick" -> options -> options.keyPickItem;
            case "swap" -> options -> options.keySwapOffhand;
            case "screenshot" -> options -> options.keyScreenshot;
            case "fullscreen" -> options -> options.keyFullscreen;
            case "perspective" -> options -> options.keyTogglePerspective;
            case "playerlist" -> options -> options.keyPlayerList;
            case "advancements" -> options -> options.keyAdvancements;
            default -> hotbar(name);
        };
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
     */
    static Class<? extends Screen> screenClass(String name) {
        String bare = name.trim();
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
                // try the next package
            }
        }
        throw new ScenarioJsonException(
            "no screen class named '" + name + "' (try a fully qualified name)"
        );
    }
}
