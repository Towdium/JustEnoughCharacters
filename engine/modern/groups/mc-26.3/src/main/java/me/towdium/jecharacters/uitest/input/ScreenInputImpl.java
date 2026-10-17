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

public final class ScreenInputImpl implements ScreenInput {

    @Override
    public Object currentScreen() {
        return Minecraft.getInstance().gui.screen();
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
        ((Screen) screen).keyPressed(keyEvent(keyCode, modifiers));
    }

    @Override
    public void keyReleased(Object screen, int keyCode, int scanCode, int modifiers) {
        ((Screen) screen).keyReleased(keyEvent(keyCode, modifiers));
    }

    @Override
    public void charTyped(Object screen, int codePoint, int modifiers) {
        ((Screen) screen).charTyped(new CharacterEvent(codePoint));
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
        MouseHandler mouse = (MouseHandler) handler;
        accessor.setMouseGrabbed(false);
        InputConstants.releaseMouse(Minecraft.getInstance().getWindow(), mouse.xpos(), mouse.ypos());
        return true;
    }

    private static KeyEvent keyEvent(int keyCode, int modifiers) {
        return new KeyEvent(keyCode, Keys.shortcutKey(keyCode), Keys.modifierBits(modifiers));
    }

    private MouseButtonEvent buttonEvent(float x, float y, int button, int modifiers) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, modifiers));
    }
}
