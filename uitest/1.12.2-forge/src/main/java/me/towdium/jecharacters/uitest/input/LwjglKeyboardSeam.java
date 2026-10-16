package me.towdium.jecharacters.uitest.input;

import org.lwjgl.input.Keyboard;

public final class LwjglKeyboardSeam {

    private LwjglKeyboardSeam() {}

    public static boolean controls() {
        return KeyState.isControlling();
    }

    public static boolean isKeyDown(int key) {
        return KeyState.isControlling() ? KeyState.isDown(key) : Keyboard.isKeyDown(key);
    }

    public static boolean next() {
        return KeyState.isControlling() ? KeyState.next() : Keyboard.next();
    }

    public static int getEventKey() {
        return KeyState.isControlling() ? KeyState.currentKey() : Keyboard.getEventKey();
    }

    public static char getEventCharacter() {
        return KeyState.isControlling() ? KeyState.currentChar() : Keyboard.getEventCharacter();
    }

    public static boolean getEventKeyState() {
        return KeyState.isControlling() ? KeyState.currentState() : Keyboard.getEventKeyState();
    }

    public static boolean isRepeatEvent() {
        return !KeyState.isControlling() && Keyboard.isRepeatEvent();
    }

    public static long getEventNanoseconds() {
        return KeyState.isControlling() ? System.nanoTime() : Keyboard.getEventNanoseconds();
    }
}
