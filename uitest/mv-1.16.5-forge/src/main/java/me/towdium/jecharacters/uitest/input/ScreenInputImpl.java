package me.towdium.jecharacters.uitest.input;

import me.towdium.jecharacters.mixin.uitest.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputMappings;
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
    public boolean mouseGrabbed(Object handler) {
        return ((MouseHelper) handler).isMouseGrabbed();
    }

    @Override
    public boolean releaseMouseGrab(Object handler) {
        MouseHandlerAccessor accessor = handler instanceof MouseHandlerAccessor
                ? (MouseHandlerAccessor) handler : null;
        if (accessor == null) {
            return false;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        double[] cursorXOut = new double[1];
        double[] cursorYOut = new double[1];
        GLFW.glfwGetCursorPos(window, cursorXOut, cursorYOut);
        accessor.setMouseGrabbed(false);
        InputMappings.grabOrReleaseMouse(window, GLFW.GLFW_CURSOR_NORMAL, cursorXOut[0], cursorYOut[0]);
        return true;
    }
}
