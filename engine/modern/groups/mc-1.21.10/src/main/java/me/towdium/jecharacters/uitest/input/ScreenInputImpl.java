package me.towdium.jecharacters.uitest.input;

import com.mojang.blaze3d.platform.InputConstants;
import me.towdium.jecharacters.mixin.uitest.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
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
        ((Screen) screen).mouseClicked(buttonEvent(x, y, button, modifiers), false);
    }

    @Override
    public void mouseReleased(Object screen, float x, float y, int button, int modifiers) {
        ((Screen) screen).mouseReleased(buttonEvent(x, y, button, modifiers));
    }

    @Override
    public void mouseDragged(Object screen, float x, float y, int button, int modifiers, float deltaX, float deltaY) {
        ((Screen) screen).mouseDragged(buttonEvent(x, y, button, modifiers), deltaX, deltaY);
    }

    @Override
    public void mouseScrolled(Object screen, float x, float y, double amount) {
        ((Screen) screen).mouseScrolled(x, y, 0.0, amount);
    }

    @Override
    public void keyPressed(Object screen, int keyCode, int scanCode, int modifiers) {
        ((Screen) screen).keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
    }

    @Override
    public void keyReleased(Object screen, int keyCode, int scanCode, int modifiers) {
        ((Screen) screen).keyReleased(new KeyEvent(keyCode, scanCode, modifiers));
    }

    @Override
    public void charTyped(Object screen, int codePoint, int modifiers) {
        ((Screen) screen).charTyped(new CharacterEvent(codePoint, modifiers));
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
        long window = Minecraft.getInstance().getWindow().handle();
        double[] cursorXOut = new double[1];
        double[] cursorYOut = new double[1];
        GLFW.glfwGetCursorPos(window, cursorXOut, cursorYOut);
        accessor.setMouseGrabbed(false);
        InputConstants.grabOrReleaseMouse(
            Minecraft.getInstance().getWindow(), GLFW.GLFW_CURSOR_NORMAL, cursorXOut[0], cursorYOut[0]
        );
        return true;
    }

    private MouseButtonEvent buttonEvent(float x, float y, int button, int modifiers) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, modifiers));
    }
}
