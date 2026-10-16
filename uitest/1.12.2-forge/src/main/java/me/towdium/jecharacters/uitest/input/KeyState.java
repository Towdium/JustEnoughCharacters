package me.towdium.jecharacters.uitest.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class KeyState {

    private static volatile boolean controlling;

    private static final Set<Integer> down =
            Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());

    private static final ConcurrentLinkedQueue<KeyEvent> queue = new ConcurrentLinkedQueue<KeyEvent>();

    private static int currentKey;
    private static char currentChar;
    private static boolean currentState;

    private KeyState() {}

    public static boolean isControlling() {
        return controlling;
    }

    public static void setControlling(boolean value) {
        controlling = value;
        if (!value) clear();
    }

    public static void clear() {
        queue.clear();
        down.clear();
        currentKey = 0;
        currentChar = 0;
        currentState = false;
    }

    public static void setDown(int key, boolean value) {
        if (value) down.add(key);
        else down.remove(key);
    }

    public static boolean isDown(int key) {
        return down.contains(key);
    }

    public static void enqueue(int key, char character, boolean state) {
        queue.add(new KeyEvent(key, character, state));
    }

    public static void tap(int key, char character) {
        enqueue(key, character, true);
        enqueue(key, character, false);
    }

    public static void type(String text) {
        for (int i = 0; i < text.length(); i++) {
            enqueue(0, text.charAt(i), true);
        }
    }

    public static boolean next() {
        KeyEvent event = queue.poll();
        if (event == null) return false;
        currentKey = event.key;
        currentChar = event.character;
        currentState = event.state;
        return true;
    }

    public static int currentKey() {
        return currentKey;
    }

    public static char currentChar() {
        return currentChar;
    }

    public static boolean currentState() {
        return currentState;
    }

    public static List<String> pending() {
        List<String> ret = new ArrayList<String>();
        for (KeyEvent event : queue) ret.add(event.toString());
        return ret;
    }

    private static final class KeyEvent {
        final int key;
        final char character;
        final boolean state;

        KeyEvent(int key, char character, boolean state) {
            this.key = key;
            this.character = character;
            this.state = state;
        }

        @Override
        public String toString() {
            return (state ? "down" : "up") + " key=" + key + " char=" + (int) character;
        }
    }
}
