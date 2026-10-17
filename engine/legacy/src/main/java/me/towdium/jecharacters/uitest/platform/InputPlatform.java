package me.towdium.jecharacters.uitest.platform;

import java.util.function.Function;

public interface InputPlatform {

    Object options();

    Function hotbarSlotBind(int slot);

    String bindName(Object mapping);

    Object bindKey(Object mapping);

    int pressableKeyOf(Object mapping);

    boolean mouseBind(Object mapping);

    int[] modifierKeysOf(Object mapping);

    void setBindDown(Object mapping, boolean down);

    boolean invertYMouse();

    double turnSensitivity();

    boolean smoothCamera();

    void setSmoothCamera(boolean value);

    boolean worldLoaded();

    float playerYaw();

    float playerPitch();

    float playerPrevYaw();

    float playerPrevPitch();

    boolean firstPersonScoping();

    float wrapDegrees(float degrees);

    float clamp(float value, float min, float max);

    float lerp(float delta, float start, float end);

    float cameraYaw();

    float cameraPitch();

    static void register(InputPlatform platform) {
        Holder.platform = platform;
    }

    static InputPlatform current() {
        return Holder.platform;
    }

    final class Holder {

        static InputPlatform platform;
    }
}
