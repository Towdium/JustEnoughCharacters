package me.towdium.jecharacters.uitest.api;

import me.towdium.jecharacters.uitest.input.ScreenInput;

public abstract class InputDriver {

    public static final float parked = -10_000f;

    public abstract void install();

    public abstract void uninstall();

    public abstract boolean installed();

    public abstract boolean usesVirtualCursor();

    public abstract void placeCursor(float xGui, float yGui);

    public void moveTo(Object screen, float x, float y) {}

    public void mouseDown(Object screen, float x, float y, int button) {}

    public void mouseUp(Object screen, float x, float y, int button) {}

    public void dragTo(Object screen, float x, float y, int button, float deltaX, float deltaY) {}

    public void mouseDownThroughHandler(Object screen, float x, float y, int button) {}

    public void mouseUpThroughHandler(Object screen, float x, float y, int button) {}

    public void scroll(Object screen, float x, float y, double amount) {}

    public void keyDown(Object screen, int keyCode, int scanCode, int modifiers) {}

    public void keyUp(Object screen, int keyCode, int scanCode, int modifiers) {}

    public void charTyped(Object screen, int codePoint, int modifiers) {}

    public void imeCompose(String preedit) {
        ScreenInput.current().imeCompose(preedit);
    }

    public void imeCommit(String committed) {
        ScreenInput.current().imeCommit(committed);
    }

    public String imeFocusedText() {
        return ScreenInput.current().imeFocusedText();
    }

    public boolean imeFocusedPreeditOverlay() {
        return ScreenInput.current().imeFocusedPreeditOverlay();
    }

    public abstract boolean isKeyDown(int keyCode);

    public abstract PlayerInput playerInput();
}
