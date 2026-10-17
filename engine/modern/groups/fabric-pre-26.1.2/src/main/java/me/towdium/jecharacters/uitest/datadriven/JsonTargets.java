package me.towdium.jecharacters.uitest.datadriven;

import me.towdium.jecharacters.mixin.uitest.KeyMappingAccessor;
import me.towdium.jecharacters.uitest.input.Keys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

final class JsonTargets {

    private JsonTargets() {}

    static int key(String name) {
        try {
            return Keys.key(name);
        } catch (IllegalArgumentException e) {
            throw new ScenarioJsonException(e.getMessage(), e);
        }
    }

    static int modifierMask(List<String> names) {
        int mask = 0;
        for (String name : names) {
            mask |= switch (name.trim().toUpperCase(Locale.ROOT)) {
                case "CTRL", "CONTROL" -> Keys.controlMask;
                case "SHIFT" -> Keys.shiftMask;
                case "ALT" -> Keys.altMask;
                case "SUPER", "META", "CMD", "COMMAND" -> Keys.superMask;
                default -> throw new ScenarioJsonException(
                    "modifier '" + name + "' is not one of ctrl, shift, alt, super"
                );
            };
        }
        return mask;
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
        screens.put("net.minecraft.client.gui.screens.ChatScreen", ChatScreen.class);
        screens.put("CreativeModeInventoryScreen", CreativeModeInventoryScreen.class);
        screens.put(
            "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen",
            CreativeModeInventoryScreen.class
        );
        return Map.copyOf(screens);
    }
}
