package me.towdium.jecharacters.uitest.input;

import com.mojang.blaze3d.platform.InputConstants;
import me.towdium.jecharacters.mixin.uitest.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public final class ScreenInputImpl implements ScreenInput {

    @Override
    public Object currentScreen() {
        return Minecraft.getInstance().screen;
    }

    @Override
    public void mouseMoved(Object screen, float x, float y) {
        ((Screen) screen).mouseMoved(x, y);
    }

    @Override
    public void mouseClicked(Object screen, float x, float y, int button, int modifiers) {
        ((Screen) screen).mouseClicked(x, y, button);
    }

    @Override
    public void mouseReleased(Object screen, float x, float y, int button, int modifiers) {
        ((Screen) screen).mouseReleased(x, y, button);
    }

    @Override
    public void mouseDragged(Object screen, float x, float y, int button, int modifiers, float deltaX, float deltaY) {
        ((Screen) screen).mouseDragged(x, y, button, deltaX, deltaY);
    }

    @Override
    public void mouseScrolled(Object screen, float x, float y, double amount) {
        ((Screen) screen).mouseScrolled(x, y, amount);
    }

    @Override
    public void keyPressed(Object screen, int keyCode, int scanCode, int modifiers) {
        ((Screen) screen).keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void keyReleased(Object screen, int keyCode, int scanCode, int modifiers) {
        ((Screen) screen).keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void charTyped(Object screen, int codePoint, int modifiers) {
        ((Screen) screen).charTyped((char) codePoint, modifiers);
    }

    @Override
    public void clickThroughHandler(float xGui, float yGui, int button, int action) {
        MouseHandler mouseHandler = Minecraft.getInstance().mouseHandler;
        if (!(mouseHandler instanceof MouseHandlerAccessor)) {
            throw new IllegalStateException(
                "MouseHandlerAccessor is not applied; the harness cannot click through the mouse handler"
            );
        }
        MouseHandlerAccessor accessor = (MouseHandlerAccessor) mouseHandler;
        accessor.setXpos(CursorState.toPhysicalX(xGui));
        accessor.setYpos(CursorState.toPhysicalY(yGui));
        long window = Minecraft.getInstance().getWindow().getWindow();
        RawInputGate.runSynthetic(() -> accessor.invokeOnPress(window, button, action, 0));
    }

    @Override
    public boolean mouseGrabbed(Object handler) {
        return ((MouseHandler) handler).isMouseGrabbed();
    }

    @Override
    public boolean releaseMouseGrab(Object handler) {
        if (!(handler instanceof MouseHandlerAccessor)) {
            return false;
        }
        MouseHandlerAccessor accessor = (MouseHandlerAccessor) handler;
        long window = Minecraft.getInstance().getWindow().getWindow();
        double[] cursorXOut = new double[1];
        double[] cursorYOut = new double[1];
        GLFW.glfwGetCursorPos(window, cursorXOut, cursorYOut);
        accessor.setMouseGrabbed(false);
        InputConstants.grabOrReleaseMouse(window, GLFW.GLFW_CURSOR_NORMAL, cursorXOut[0], cursorYOut[0]);
        return true;
    }
}
