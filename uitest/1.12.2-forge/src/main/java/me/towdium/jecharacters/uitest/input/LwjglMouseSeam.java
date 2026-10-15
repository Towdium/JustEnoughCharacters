package me.towdium.jecharacters.uitest.input;

import org.lwjgl.input.Mouse;

/**
 * The pointer half of the input seam: one static method per {@code org.lwjgl.input.Mouse} read,
 * with the same descriptor, answered from {@link CursorState} and forwarded to the real LWJGL read
 * whenever the harness is not driving. See {@link LwjglKeyboardSeam} for the rewrite shape and the
 * port note; this is the {@code MouseHandlerMixin} equivalent.
 */
public final class LwjglMouseSeam {

    private LwjglMouseSeam() {}

    /** Whether the harness owns pointer input: the guard every read below tests. */
    public static boolean controls() {
        return CursorState.isControlling();
    }

    public static boolean isButtonDown(int button) {
        return CursorState.isControlling() ? CursorState.isDown(button) : Mouse.isButtonDown(button);
    }

    public static boolean next() {
        return CursorState.isControlling() ? CursorState.next() : Mouse.next();
    }

    public static int getEventButton() {
        return CursorState.isControlling() ? CursorState.eventButton() : Mouse.getEventButton();
    }

    public static boolean getEventButtonState() {
        return CursorState.isControlling() ? CursorState.eventButtonState() : Mouse.getEventButtonState();
    }

    public static int getEventX() {
        return CursorState.isControlling() ? CursorState.eventX() : Mouse.getEventX();
    }

    public static int getEventY() {
        return CursorState.isControlling() ? CursorState.eventY() : Mouse.getEventY();
    }

    public static int getEventDX() {
        return CursorState.isControlling() ? CursorState.eventDX() : Mouse.getEventDX();
    }

    public static int getEventDY() {
        return CursorState.isControlling() ? CursorState.eventDY() : Mouse.getEventDY();
    }

    public static int getEventDWheel() {
        return CursorState.isControlling() ? CursorState.eventDWheel() : Mouse.getEventDWheel();
    }

    public static long getEventNanoseconds() {
        return CursorState.isControlling() ? System.nanoTime() : Mouse.getEventNanoseconds();
    }

    public static int getX() {
        return CursorState.isControlling() ? CursorState.x() : Mouse.getX();
    }

    public static int getY() {
        return CursorState.isControlling() ? CursorState.y() : Mouse.getY();
    }

    public static int getDX() {
        return CursorState.isControlling() ? CursorState.dx() : Mouse.getDX();
    }

    public static int getDY() {
        return CursorState.isControlling() ? CursorState.dy() : Mouse.getDY();
    }
}
