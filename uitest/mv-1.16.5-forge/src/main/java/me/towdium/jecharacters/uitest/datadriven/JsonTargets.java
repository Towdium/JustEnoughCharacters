package me.towdium.jecharacters.uitest.datadriven;

import me.towdium.jecharacters.mixin.uitest.KeyMappingAccessor;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.GameSettings;
import net.minecraft.client.gui.screen.Screen;
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
            if (value instanceof Integer && ((Integer) value) != GLFW.GLFW_KEY_UNKNOWN) {
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
            String modifier = name.trim().toUpperCase(Locale.ROOT);
            switch (modifier) {
                case "CTRL":
                case "CONTROL":
                    mask |= GLFW.GLFW_MOD_CONTROL;
                    break;
                case "SHIFT":
                    mask |= GLFW.GLFW_MOD_SHIFT;
                    break;
                case "ALT":
                    mask |= GLFW.GLFW_MOD_ALT;
                    break;
                case "SUPER":
                case "META":
                case "CMD":
                case "COMMAND":
                    mask |= GLFW.GLFW_MOD_SUPER;
                    break;
                default:
                    throw new ScenarioJsonException(
                        "modifier '" + name + "' is not one of ctrl, shift, alt, super"
                    );
            }
        }
        return mask;
    }

    /** The bind a name stands for, as the options-shaped lookup the builder takes. */
    static Function<GameSettings, KeyBinding> bind(String name) {
        Function<GameSettings, KeyBinding> vanilla = vanillaBind(name.trim().toLowerCase(Locale.ROOT));
        if (vanilla != null) {
            return vanilla;
        }
        return options -> requireMapping(name);
    }

    private static KeyBinding requireMapping(String name) {
        Map<String, KeyBinding> all = KeyMappingAccessor.jechtestAll();
        KeyBinding mapping = all.get(name);
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
    private static Function<GameSettings, KeyBinding> vanillaBind(String name) {
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
    private static Function<GameSettings, KeyBinding> hotbar(String name) {
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
        candidates.add("net.minecraft.client.gui.screen." + bare);
        candidates.add("net.minecraft.client.gui.screen.inventory." + bare);
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
