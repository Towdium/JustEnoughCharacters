package me.towdium.jecharacters.uitest.platform;

import com.mojang.blaze3d.platform.InputConstants;
import me.towdium.jecharacters.mixin.uitest.KeyboardHandlerAccessor;
import me.towdium.jecharacters.mixin.uitest.MouseHandlerAccessor;
import net.minecraft.client.KeyMapping;

public interface InputPlatform {

    InputConstants.Key keyOf(KeyMapping mapping);

    int[] modifierKeysOf(KeyMapping mapping);

    void pressKey(
        KeyboardHandlerAccessor keyboard, long window, int glfwKey, int scanCode, int action, int modifiers
    );

    void typeChar(KeyboardHandlerAccessor keyboard, long window, int codePoint);

    void pressMouse(MouseHandlerAccessor mouse, long window, int button, int action);

    boolean invertYMouse();

    double turnSensitivity();

    float partialTick();

    long window();

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
