package me.towdium.jecharacters.uitest.api;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.GameSettings;

import java.util.List;
import java.util.function.Function;

public interface PlayerInput {

    void pressKey(int glfwKey);

    void pressKey(int glfwKey, int modifiers);

    void holdKey(int glfwKey);

    void releaseKey(int glfwKey);

    boolean isHeld(int glfwKey);

    void pressBind(Function<GameSettings, KeyBinding> bind);

    void holdBind(Function<GameSettings, KeyBinding> bind);

    void releaseBind(Function<GameSettings, KeyBinding> bind);

    void typeText(String text);

    void pumpTick();

    void pumpFrame();

    List<CameraFrame> cameraTrace();

    long positionCorrections();

    void cancelSweep();

    void releaseAll();

    boolean active();
}
