package me.towdium.jecharacters.uitest.platform;

import com.mojang.blaze3d.platform.InputConstants;
import me.towdium.jecharacters.mixin.uitest.KeyboardHandlerAccessor;
import me.towdium.jecharacters.mixin.uitest.MouseHandlerAccessor;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FabricInputPlatform implements InputPlatform {

    @Override
    public InputConstants.Key keyOf(KeyMapping mapping) {
        InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(mapping);
        if (!InputConstants.UNKNOWN.equals(key)) {
            return key;
        }
        String declared = declaredBinding(mapping);
        if (declared == null) {
            return key;
        }
        InputConstants.Key fallback = InputConstants.getKey(keyPart(declared));
        // Reading the preset is not enough: the game matches on the mapping itself, which a mapping
        // registered after the options were built never had filled in, so put the value back.
        mapping.setKey(fallback);
        return fallback;
    }

    @Override
    public int[] modifierKeysOf(KeyMapping mapping) {
        int[] amecs = amecsModifiersOf(mapping);
        if (amecs != null) {
            return amecs;
        }
        String declared = declaredBinding(mapping);
        return declared == null ? new int[0] : modifierKeysOf(modifierPart(declared));
    }

    /**
     * A mod's binding is not always readable here: a mapping registered after the options were built
     * never receives its `options.txt` value and stays unknown (JEI's focus key on this row), so the
     * value the preset declares for it is the only key left to press.
     */
    private static String declaredBinding(KeyMapping mapping) {
        Path options = Minecraft.getInstance().gameDirectory.toPath().resolve("options.txt");
        if (!Files.isRegularFile(options)) {
            return null;
        }
        String prefix = "key_" + mapping.getName() + ":";
        try {
            for (String line : Files.readAllLines(options)) {
                if (line.startsWith(prefix)) {
                    return line.substring(prefix.length()).trim();
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static String keyPart(String declared) {
        int colon = declared.indexOf(':');
        return colon < 0 ? declared : declared.substring(0, colon);
    }

    private static String modifierPart(String declared) {
        int colon = declared.indexOf(':');
        return colon < 0 ? "" : declared.substring(colon + 1);
    }

    private static int[] modifierKeysOf(String declared) {
        List<Integer> keys = new ArrayList<>(3);
        for (String name : declared.split("[+: ]")) {
            switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "shift" -> keys.add(GLFW.GLFW_KEY_LEFT_SHIFT);
                case "control", "ctrl" -> keys.add(GLFW.GLFW_KEY_LEFT_CONTROL);
                case "alt" -> keys.add(GLFW.GLFW_KEY_LEFT_ALT);
                default -> {
                }
            }
        }
        int[] result = new int[keys.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = keys.get(i);
        }
        return result;
    }

    /**
     * amecs (the real one and the api Too Many Shortcuts ships) binds the modifiers to the mapping and
     * a modifier-aware match demands really holding them; none of this is visible to the vanilla key.
     */
    private static int[] amecsModifiersOf(KeyMapping mapping) {
        try {
            Class<?> utils = Class.forName("de.siphalor.amecs.api.KeyBindingUtils");
            Object modifiers = utils.getMethod("getBoundModifiers", KeyMapping.class).invoke(null, mapping);
            if (modifiers == null) {
                return null;
            }
            List<Integer> keys = new ArrayList<>(3);
            addModifier(keys, modifiers, "getShift", GLFW.GLFW_KEY_LEFT_SHIFT);
            addModifier(keys, modifiers, "getControl", GLFW.GLFW_KEY_LEFT_CONTROL);
            addModifier(keys, modifiers, "getAlt", GLFW.GLFW_KEY_LEFT_ALT);
            int[] result = new int[keys.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = keys.get(i);
            }
            return result;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static void addModifier(List<Integer> keys, Object modifiers, String getter, int glfwKey) {
        try {
            if (Boolean.TRUE.equals(modifiers.getClass().getMethod(getter).invoke(modifiers))) {
                keys.add(glfwKey);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Override
    public void pressKey(
        KeyboardHandlerAccessor keyboard, long window, int glfwKey, int scanCode, int action, int modifiers
    ) {
        Minecraft.getInstance().keyboardHandler.keyPress(window, glfwKey, scanCode, action, modifiers);
    }

    @Override
    public void typeChar(KeyboardHandlerAccessor keyboard, long window, int codePoint) {
        keyboard.invokeCharTyped(window, codePoint, 0);
    }

    @Override
    public void pressMouse(MouseHandlerAccessor mouse, long window, int button, int action) {
        mouse.invokeOnPress(window, button, action, 0);
    }

    @Override
    public boolean invertYMouse() {
        return Minecraft.getInstance().options.invertYMouse().get();
    }

    @Override
    public double turnSensitivity() {
        Options options = Minecraft.getInstance().options;
        if (options.smoothCamera) {
            throw new IllegalStateException("the cinematic camera is on; an exact scripted turn needs it off");
        }
        return options.sensitivity().get() * 0.6 + 0.2;
    }

    @Override
    public float partialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
    }

    @Override
    public long window() {
        return Minecraft.getInstance().getWindow().getWindow();
    }

    @Override
    public float cameraYaw() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
    }

    @Override
    public float cameraPitch() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getXRot();
    }
}
