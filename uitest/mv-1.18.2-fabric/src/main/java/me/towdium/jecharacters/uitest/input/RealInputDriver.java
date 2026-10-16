package me.towdium.jecharacters.uitest.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

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

    public void warpOsCursor(float xGui, float yGui) {
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        GLFW.glfwSetCursorPos(window, CursorState.toPhysicalX(xGui), CursorState.toPhysicalY(yGui));
    }
}
