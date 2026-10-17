package me.towdium.jecharacters.uitest.platform;

import com.mojang.blaze3d.platform.InputConstants;
import me.towdium.jecharacters.mixin.uitest.KeyboardHandlerAccessor;
import me.towdium.jecharacters.mixin.uitest.MouseHandlerAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.extensions.IKeyMappingExtension;
import net.neoforged.neoforge.client.settings.KeyModifier;

import java.util.ArrayList;
import java.util.List;

public final class NeoForgeInputPlatform implements InputPlatform {

    @Override
    public InputConstants.Key keyOf(KeyMapping mapping) {
        return mapping.getKey();
    }

    @Override
    public int[] modifierKeysOf(KeyMapping mapping) {
        if (!(mapping instanceof IKeyMappingExtension extension)) {
            return new int[0];
        }
        KeyModifier modifier = extension.getKeyModifier();
        if (modifier == null) return new int[0];
        List<Integer> keys = new ArrayList<>(2);
        for (InputConstants.Key code : modifier.codes()) {
            if (code.getType() == InputConstants.Type.KEYSYM && code.getValue() != InputConstants.UNKNOWN.getValue()) {
                keys.add(code.getValue());
            }
        }
        int[] result = new int[keys.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = keys.get(i);
        }
        return result;
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
        var values = ClientHooks.getTurnPlayerValues(options.sensitivity().get(), options.smoothCamera);
        if (values.getCinematicCameraEnabled()) {
            throw new IllegalStateException("the cinematic camera is on; an exact scripted turn needs it off");
        }
        return values.getMouseSensitivity() * 0.6 + 0.2;
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
