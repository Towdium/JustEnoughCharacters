package me.towdium.jecharacters.uitest.input;

public interface ScreenInput {

    Object currentScreen();

    void mouseMoved(Object screen, float x, float y);

    void mouseClicked(Object screen, float x, float y, int button, int modifiers);

    void mouseReleased(Object screen, float x, float y, int button, int modifiers);

    void mouseDragged(Object screen, float x, float y, int button, int modifiers, float deltaX, float deltaY);

    void mouseScrolled(Object screen, float x, float y, double amount);

    void keyPressed(Object screen, int keyCode, int scanCode, int modifiers);

    void keyReleased(Object screen, int keyCode, int scanCode, int modifiers);

    void charTyped(Object screen, int codePoint, int modifiers);

    default void clickThroughHandler(float xGui, float yGui, int button, int action) {
        throw new UnsupportedOperationException("this row does not click through the mouse handler");
    }

    boolean mouseGrabbed(Object handler);

    boolean releaseMouseGrab(Object handler);

    static void register(ScreenInput input) {
        Holder.input = input;
    }

    static ScreenInput current() {
        return Holder.input;
    }

    final class Holder {

        static ScreenInput input;
    }
}
