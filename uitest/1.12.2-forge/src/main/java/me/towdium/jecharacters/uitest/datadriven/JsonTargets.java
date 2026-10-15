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

/**
 * Turning the names a JSON step uses into the things the engine presses.
 *
 * <p>Three kinds of name are resolved here, all of them at define time so a bad name is a reported
 * failure rather than a step that quietly does nothing:
 *
 * <ul>
 *   <li><b>a LWJGL key</b> — {@code "F"}, {@code "ESCAPE"}, {@code "LCONTROL"} — read off LWJGL 2's
 *       own {@link Keyboard#getKeyIndex(String) key table}, so the vocabulary is the platform's;</li>
 *   <li><b>a modifier mask</b> — {@code "CTRL"}, {@code "SHIFT"}, {@code "ALT"}, {@code "SUPER"} —
 *       mapped to the GLFW-style bits a real keystroke would carry, which is what the builder's
 *       gesture expansion reads;</li>
 *   <li><b>a key bind</b> — either a friendly name for one of vanilla's own ({@code "inventory"},
 *       {@code "chat"}, …) or the mapping's own name as the game spells it
 *       ({@code "key.jei.focusSearch"}).</li>
 * </ul>
 *
 * <p><b>Port note.</b> The 1.21.1 class of the same name, re-rooted:
 *
 * <ul>
 *   <li>keys are LWJGL 2 names rather than GLFW's. GLFW spellings that LWJGL 2 spells differently
 *       ({@code LEFT_CONTROL}, {@code ENTER}, {@code BACKSPACE}, {@code PAGE_UP}, …) are aliased to
 *       their LWJGL 2 forms first, so a scenario may write either; anything else must be a
 *       {@code Keyboard.getKeyName} name. There is no {@code GLFW_KEY_UNKNOWN} sentinel — LWJGL 2's
 *       is {@link Keyboard#KEY_NONE}, and a key this table returns it for is refused.</li>
 *   <li>a bind is a {@link KeyBinding} object read out of the live
 *       {@link GameSettings#keyBindings} array, not a {@code Function<Options, KeyMapping>}. Forge's
 *       {@code ClientRegistry.registerKeyBinding} appends modded bindings to that same array
 *       (ClientRegistry.java:60-63), so JEI's {@code key.jei.focusSearch} is in it alongside the
 *       vanilla ones — the registration-name lookup needs no reflection and no mod knowledge.</li>
 *   <li>a screen name is looked for under {@code net.minecraft.client.gui} and then
 *       {@code net.minecraft.client.gui.inventory}, 1.12.2's two root packages for screens, rather
 *       than 1.16+'s {@code .screens} pair.</li>
 * </ul>
 */
final class JsonTargets {

    /** GLFW-style modifier bits the builder expands back into LWJGL keys. */
    static final int modShift = 1;

    static final int modCtrl = 2;

    static final int modAlt = 4;

    static final int modSuper = 8;

    /** GLFW spellings that LWJGL 2's key table spells differently. */
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

    /**
     * The LWJGL 2 key code a name stands for; {@code "key.keyboard.f"} and {@code "F"} both work.
     *
     * <p>A GLFW-style name is normalised to its LWJGL 2 spelling first (see the alias table), so a
     * scenario written for the 1.21.1 vocabulary keeps its key names where the two platforms agree on
     * them and gets a clear refusal where they do not.
     */
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

    /**
     * The GLFW-style modifier mask a list of modifier names contributes.
     *
     * <p>The bits are the ones the builder expands back into held keys; a named modifier that is not
     * one is refused rather than ignored, because a chord with a silently missing modifier is a step
     * that does something else than what its author wrote.
     */
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

    /**
     * The bind a name stands for.
     *
     * <p>Read out of the live {@link GameSettings#keyBindings} array — vanilla's own bindings plus the
     * ones Forge appended for every mod — matching the game's own description name. The object is
     * returned, not its key code, so the scenario keeps reading the binding the player actually has:
     * a remapped key is pressed, not the default.
     */
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

    /** A friendly name for one of vanilla's own binds, or {@code null} for anything else. */
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

    /** {@code "hotbar.1"} … {@code "hotbar.9"}. */
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

    /**
     * The screen class a name stands for.
     *
     * <p>A fully qualified name is taken as it is; a bare one is looked for in the two packages
     * 1.12.2's own screens live in, which is what lets a scenario say {@code "GuiChat"}.
     */
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
                // try the next package
            }
        }
        throw new ScenarioJsonException(
                "no screen class named '" + name + "' (try a fully qualified name)");
    }
}
