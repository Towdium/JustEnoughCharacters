package me.towdium.jecharacters.uitest.api;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

import java.util.List;
import java.util.function.Function;

public interface PlayerInput {

    void pressKey(int glfwKey);

    void pressKey(int glfwKey, int modifiers);

    void holdKey(int glfwKey);

    void releaseKey(int glfwKey);

    boolean isHeld(int glfwKey);

    void pressBind(Function<Options, KeyMapping> bind);

    void holdBind(Function<Options, KeyMapping> bind);

    void releaseBind(Function<Options, KeyMapping> bind);

    void hotbarSlot(int slot);

    void typeText(String text);

    void turnBy(float dYaw, float dPitch);

    void lookSweep(float toYaw, float toPitch, int durationTicks);

    @Deprecated
    void lookSweepFrames(float toYaw, float toPitch, int frames);

    void pumpTick();

    void pumpFrame();

    List<CameraFrame> cameraTrace();

    long positionCorrections();

    boolean sweeping();

    void cancelSweep();

    void attack();

    void useItem();

    void pickBlock();

    void mouseDown(int button);

    void mouseUp(int button);

    void scroll(double amount);

    void releaseAll();

    boolean active();
}
