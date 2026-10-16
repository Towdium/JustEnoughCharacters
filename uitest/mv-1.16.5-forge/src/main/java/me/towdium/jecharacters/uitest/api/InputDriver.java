package me.towdium.jecharacters.uitest.api;

import net.minecraft.client.gui.screen.Screen;

public abstract class InputDriver {

    public static final float parked = -10_000f;

    public abstract void install();

    public abstract void uninstall();

    public abstract boolean installed();

    public abstract boolean usesVirtualCursor();

    public abstract void placeCursor(float xGui, float yGui);

    public void moveTo(Screen screen, float x, float y) {}

    public void mouseDown(Screen screen, float x, float y, int button) {}

    public void mouseUp(Screen screen, float x, float y, int button) {}

    public void dragTo(Screen screen, float x, float y, int button, float deltaX, float deltaY) {}

    public void scroll(Screen screen, float x, float y, double amount) {}

    public void keyDown(Screen screen, int keyCode, int scanCode, int modifiers) {}

    public void keyUp(Screen screen, int keyCode, int scanCode, int modifiers) {}

    public void charTyped(Screen screen, int codePoint, int modifiers) {}

    public abstract boolean isKeyDown(int keyCode);

    public abstract PlayerInput playerInput();
}
