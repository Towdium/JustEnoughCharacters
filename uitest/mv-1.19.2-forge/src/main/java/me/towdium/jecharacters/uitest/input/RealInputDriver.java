package me.towdium.jecharacters.uitest.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * The foreground-only driver: the real OS pointer is moved (through
 * {@code glfwSetCursorPos}), and input keeps flowing through the driver's own dispatch.
 *
 * <p>The difference from {@link SyntheticInputDriver} is only the cursor: no {@link CursorState}
 * source is installed, so vanilla's getters keep returning the physical position the warp just
 * set, and {@link #placeCursor} does not refresh hover — the pointer move itself is the hover
 * input. Gesture steps still dispatch to the screen directly, so clicks, drags and keys remain
 * deterministic.
 *
 * <p>It moves the user's mouse and needs a focused window; the Gradle wiring rejects it outright
 * when combined with a headless run.
 */
public final class RealInputDriver extends SyntheticInputDriver {

    @Override
    public boolean usesVirtualCursor() {
        return false;
    }

    @Override
    public void placeCursor(float xGui, float yGui) {
        setPointer(xGui, yGui);
        warpOsCursor(xGui, yGui);
    }

    /** Moves the real pointer to a GUI-scaled position, converting through the window scale. */
    public void warpOsCursor(float xGui, float yGui) {
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        GLFW.glfwSetCursorPos(window, CursorState.toPhysicalX(xGui), CursorState.toPhysicalY(yGui));
    }
}
