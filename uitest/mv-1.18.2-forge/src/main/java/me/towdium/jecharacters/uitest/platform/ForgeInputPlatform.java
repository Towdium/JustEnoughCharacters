package me.towdium.jecharacters.uitest.platform;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ForgeInputPlatform implements InputPlatform {

    private static final int[] MODIFIER_KEY_CANDIDATES = {
            GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL,
            GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT,
            GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT,
            GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER,
    };

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
        return ((KeyMapping) mapping).getKey();
    }

    @Override
    public int pressableKeyOf(Object mapping) {
        InputConstants.Key key = ((KeyMapping) mapping).getKey();
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
        return InputConstants.Type.MOUSE == ((KeyMapping) mapping).getKey().getType();
    }

    @Override
    public int[] modifierKeysOf(Object mapping) {
        KeyModifier modifier = ((KeyMapping) mapping).getKeyModifier();
        if (modifier == null) return new int[0];
        List<Integer> keys = new ArrayList<>(2);
        for (int keyCode : MODIFIER_KEY_CANDIDATES) {
            InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(keyCode);
            if (modifier.matches(key) && keyCode != InputConstants.UNKNOWN.getValue()) {
                keys.add(keyCode);
            }
        }
        int[] result = new int[keys.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = keys.get(i);
        }
        return result;
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
        return Minecraft.getInstance().player.getYRot();
    }

    @Override
    public float playerPitch() {
        return Minecraft.getInstance().player.getXRot();
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
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && Minecraft.getInstance().options.getCameraType().isFirstPerson() && player.isScoping();
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
