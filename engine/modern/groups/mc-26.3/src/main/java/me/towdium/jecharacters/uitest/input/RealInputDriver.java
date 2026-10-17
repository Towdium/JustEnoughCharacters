package me.towdium.jecharacters.uitest.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLMouse;

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
        long window = minecraft.getWindow().handle();
        SDLMouse.SDL_WarpMouseInWindow(window, (float) CursorState.toPhysicalX(xGui), (float) CursorState.toPhysicalY(yGui));
    }
}
