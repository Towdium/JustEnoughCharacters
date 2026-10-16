package me.towdium.jecharacters.uitest.input;

import org.lwjgl.input.Mouse;

public final class LwjglMouseSeam {

    private LwjglMouseSeam() {}

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
