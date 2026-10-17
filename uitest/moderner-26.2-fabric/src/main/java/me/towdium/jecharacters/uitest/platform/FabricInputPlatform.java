package me.towdium.jecharacters.uitest.platform;

import com.mojang.blaze3d.platform.InputConstants;
import me.towdium.jecharacters.mixin.uitest.KeyboardHandlerAccessor;
import me.towdium.jecharacters.mixin.uitest.MouseHandlerAccessor;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;

public final class FabricInputPlatform implements InputPlatform {

    @Override
    public InputConstants.Key keyOf(KeyMapping mapping) {
        return KeyMappingHelper.getBoundKeyOf(mapping);
    }

    @Override
    public int[] modifierKeysOf(KeyMapping mapping) {
        return new int[0];
    }

    @Override
    public void pressKey(
        KeyboardHandlerAccessor keyboard, long window, int glfwKey, int scanCode, int action, int modifiers
    ) {
        keyboard.invokeKeyPress(window, action, new KeyEvent(glfwKey, scanCode, modifiers));
    }

    @Override
    public void typeChar(KeyboardHandlerAccessor keyboard, long window, int codePoint) {
        keyboard.invokeCharTyped(window, new CharacterEvent(codePoint));
    }

    @Override
    public void pressMouse(MouseHandlerAccessor mouse, long window, int button, int action) {
        mouse.invokeOnButton(window, new MouseButtonInfo(button, 0), action);
    }

    @Override
    public boolean invertYMouse() {
        return Minecraft.getInstance().options.invertMouseY().get();
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
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }

    @Override
    public long window() {
        return Minecraft.getInstance().getWindow().handle();
    }

    @Override
    public float cameraYaw() {
        return Minecraft.getInstance().gameRenderer.mainCamera().yRot();
    }

    @Override
    public float cameraPitch() {
        return Minecraft.getInstance().gameRenderer.mainCamera().xRot();
    }
}
