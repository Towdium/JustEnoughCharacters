package me.towdium.jecharacters.uitest.platform;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.util.Mth;

import java.util.function.Function;

public final class FabricInputPlatform implements InputPlatform {

    @Override
    public Object options() {
        return Minecraft.getInstance().options;
    }

    @Override
    public Function<Options, KeyMapping> hotbarSlotBind(int slot) {
        return options -> options.keyHotbarSlots[slot];
    }

    @Override
    public String bindName(Object mapping) {
        return ((KeyMapping) mapping).getName();
    }

    @Override
    public Object bindKey(Object mapping) {
        return KeyBindingHelper.getBoundKeyOf((KeyMapping) mapping);
    }

    @Override
    public int pressableKeyOf(Object mapping) {
        InputConstants.Key key = KeyBindingHelper.getBoundKeyOf((KeyMapping) mapping);
        if (key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()) {
            return key.getValue();
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return key.getValue();
        }
        return -1;
    }

    @Override
    public boolean mouseBind(Object mapping) {
        return InputConstants.Type.MOUSE == KeyBindingHelper.getBoundKeyOf((KeyMapping) mapping).getType();
    }

    @Override
    public int[] modifierKeysOf(Object mapping) {
        return new int[0];
    }

    @Override
    public void setBindDown(Object mapping, boolean down) {
        ((KeyMapping) mapping).setDown(down);
    }

    @Override
    public boolean invertYMouse() {
        return Minecraft.getInstance().options.invertYMouse;
    }

    @Override
    public double turnSensitivity() {
        Options options = Minecraft.getInstance().options;
        if (options.smoothCamera) {
            throw new IllegalStateException("the cinematic camera is on; an exact scripted turn needs it off");
        }
        return options.sensitivity * 0.6 + 0.2;
    }

    @Override
    public boolean smoothCamera() {
        return Minecraft.getInstance().options.smoothCamera;
    }

    @Override
    public void setSmoothCamera(boolean value) {
        Minecraft.getInstance().options.smoothCamera = value;
    }

    @Override
    public boolean worldLoaded() {
        return Minecraft.getInstance().player != null;
    }

    @Override
    public float playerYaw() {
        return Minecraft.getInstance().player.yRot;
    }

    @Override
    public float playerPitch() {
        return Minecraft.getInstance().player.xRot;
    }

    @Override
    public float playerPrevYaw() {
        return Minecraft.getInstance().player.yRotO;
    }

    @Override
    public float playerPrevPitch() {
        return Minecraft.getInstance().player.xRotO;
    }

    @Override
    public boolean firstPersonScoping() {
        return false;
    }

    @Override
    public float wrapDegrees(float degrees) {
        return Mth.wrapDegrees(degrees);
    }

    @Override
    public float clamp(float value, float min, float max) {
        return Mth.clamp(value, min, max);
    }

    @Override
    public float lerp(float delta, float start, float end) {
        return Mth.lerp(delta, start, end);
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
