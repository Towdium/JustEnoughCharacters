package me.towdium.jecharacters.uitest.platform;

import com.mojang.blaze3d.platform.InputConstants;
import me.towdium.jecharacters.mixin.uitest.KeyboardHandlerAccessor;
import me.towdium.jecharacters.mixin.uitest.MouseHandlerAccessor;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

public final class FabricInputPlatform implements InputPlatform {

    @Override
    public InputConstants.Key keyOf(KeyMapping mapping) {
        return KeyBindingHelper.getBoundKeyOf(mapping);
    }

    @Override
    public int[] modifierKeysOf(KeyMapping mapping) {
        return new int[0];
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
