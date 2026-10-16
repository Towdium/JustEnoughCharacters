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

final class JsonTargets {

    private JsonTargets() {}

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

    static int modifierMask(List<String> names) {
        int mask = 0;
        for (String name : names) {
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

    private static Function<Options, KeyMapping> vanillaBind(String name) {
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
            }
        }
        throw new ScenarioJsonException(
            "no screen class named '" + name + "' (try a fully qualified name)"
        );
    }

    private static final Map<String, Class<? extends Screen>> vanillaScreens = vanillaScreens();

    private static Map<String, Class<? extends Screen>> vanillaScreens() {
        Map<String, Class<? extends Screen>> screens = new LinkedHashMap<>();
        screens.put("ChatScreen", ChatScreen.class);
        return Collections.unmodifiableMap(screens);
    }
}
