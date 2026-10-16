package me.towdium.jecharacters.uitest.datadriven;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class JsonTargets {

    static final int modShift = 1;

    static final int modCtrl = 2;

    static final int modAlt = 4;

    static final int modSuper = 8;

    private static final Map<String, String> keyAliases = keyAliases();

    private JsonTargets() {}

    private static Map<String, String> keyAliases() {
        Map<String, String> aliases = new LinkedHashMap<String, String>();
        aliases.put("LEFT_CONTROL", "LCONTROL");
        aliases.put("RIGHT_CONTROL", "RCONTROL");
        aliases.put("LEFT_SHIFT", "LSHIFT");
        aliases.put("RIGHT_SHIFT", "RSHIFT");
        aliases.put("LEFT_ALT", "LMENU");
        aliases.put("RIGHT_ALT", "RMENU");
        aliases.put("LEFT_SUPER", "LWIN");
        aliases.put("RIGHT_SUPER", "RWIN");
        aliases.put("ENTER", "RETURN");
        aliases.put("BACKSPACE", "BACK");
        aliases.put("ESC", "ESCAPE");
        aliases.put("PAGE_UP", "PRIOR");
        aliases.put("PAGE_DOWN", "NEXT");
        aliases.put("CAPS_LOCK", "CAPITAL");
        aliases.put("LEFT_ARROW", "LEFT");
        aliases.put("RIGHT_ARROW", "RIGHT");
        aliases.put("UP_ARROW", "UP");
        aliases.put("DOWN_ARROW", "DOWN");
        aliases.put("DEL", "DELETE");
        aliases.put("INS", "INSERT");
        return aliases;
    }

    static int lwjglKey(String name) {
        String bare = name.trim();
        if (bare.startsWith("key.keyboard.")) {
            bare = bare.substring("key.keyboard.".length());
        }
        String normalised = bare.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
        String aliased = keyAliases.get(normalised);
        if (aliased != null) {
            normalised = aliased;
        }
        int code = Keyboard.getKeyIndex(normalised);
        if (code == Keyboard.KEY_NONE) {
            throw new ScenarioJsonException(
                    "key '" + name + "' is not an LWJGL key name (expected e.g. 'f', 'escape', "
                            + "'lcontrol', which is LWJGL 2's name for left ctrl)"
            );
        }
        return code;
    }

    static int modifierMask(List<String> names) {
        int mask = 0;
        for (String name : names) {
            String normalised = name.trim().toUpperCase(Locale.ROOT);
            if ("CTRL".equals(normalised) || "CONTROL".equals(normalised)) {
                mask |= modCtrl;
            } else if ("SHIFT".equals(normalised)) {
                mask |= modShift;
            } else if ("ALT".equals(normalised)) {
                mask |= modAlt;
            } else if ("SUPER".equals(normalised) || "META".equals(normalised) || "CMD".equals(normalised)
                    || "COMMAND".equals(normalised)) {
                mask |= modSuper;
            } else {
                throw new ScenarioJsonException(
                        "modifier '" + name + "' is not one of ctrl, shift, alt, super");
            }
        }
        return mask;
    }

    static KeyBinding bind(String name) {
        GameSettings settings = Minecraft.getMinecraft().gameSettings;
        String wanted = name.trim();
        for (KeyBinding binding : settings.keyBindings) {
            if (binding.getKeyDescription().equals(wanted)) {
                return binding;
            }
        }
        String friendly = wanted.toLowerCase(Locale.ROOT);
        KeyBinding friendlyBinding = friendlyBind(settings, friendly);
        if (friendlyBinding != null) {
            return friendlyBinding;
        }
        List<String> candidates = new ArrayList<String>();
        for (KeyBinding binding : settings.keyBindings) {
            if (binding.getKeyDescription().toLowerCase(Locale.ROOT).contains(wanted.toLowerCase(Locale.ROOT))) {
                candidates.add(binding.getKeyDescription());
            }
        }
        candidates.sort(String::compareTo);
        throw new ScenarioJsonException(
                "no key bind named '" + name + "' is registered"
                        + (candidates.isEmpty() ? "" : "; did you mean " + candidates + "?")
        );
    }

    private static KeyBinding friendlyBind(GameSettings settings, String name) {
        switch (name) {
            case "chat":
                return settings.keyBindChat;
            case "command":
                return settings.keyBindCommand;
            case "inventory":
                return settings.keyBindInventory;
            case "forward":
                return settings.keyBindForward;
            case "back":
                return settings.keyBindBack;
            case "left":
                return settings.keyBindLeft;
            case "right":
                return settings.keyBindRight;
            case "jump":
                return settings.keyBindJump;
            case "sneak":
                return settings.keyBindSneak;
            case "sprint":
                return settings.keyBindSprint;
            case "drop":
                return settings.keyBindDrop;
            case "attack":
                return settings.keyBindAttack;
            case "use":
                return settings.keyBindUseItem;
            case "pick":
                return settings.keyBindPickBlock;
            case "swap":
                return settings.keyBindSwapHands;
            case "screenshot":
                return settings.keyBindScreenshot;
            case "fullscreen":
                return settings.keyBindFullscreen;
            case "perspective":
                return settings.keyBindTogglePerspective;
            case "playerlist":
                return settings.keyBindPlayerList;
            case "advancements":
                return settings.keyBindAdvancements;
            default:
                return hotbar(settings, name);
        }
    }

    private static KeyBinding hotbar(GameSettings settings, String name) {
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
        return settings.keyBindsHotbar[slot - 1];
    }

    static Class<? extends GuiScreen> screenClass(String name) {
        String bare = name.trim();
        List<String> candidates = new ArrayList<String>();
        candidates.add(bare);
        candidates.add("net.minecraft.client.gui." + bare);
        candidates.add("net.minecraft.client.gui.inventory." + bare);
        for (String candidate : candidates) {
            try {
                Class<?> type = Class.forName(candidate);
                if (GuiScreen.class.isAssignableFrom(type)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends GuiScreen> screen = (Class<? extends GuiScreen>) type;
                    return screen;
                }
                throw new ScenarioJsonException(candidate + " is not a GuiScreen");
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new ScenarioJsonException(
                "no screen class named '" + name + "' (try a fully qualified name)");
    }
}
