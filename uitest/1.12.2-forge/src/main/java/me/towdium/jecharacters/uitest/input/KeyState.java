package me.towdium.jecharacters.uitest.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The harness's keyboard state, and the queue of key events it has not delivered yet.
 *
 * <p>On 1.21.1 the equivalent state is read by a set of Mixins into vanilla's own
 * {@code KeyboardHandler}/{@code InputConstants} wrappers. This line has no such wrappers: the game
 * calls {@code org.lwjgl.input.Keyboard} directly, and the harness's coremod
 * ({@link me.towdium.jecharacters.uitest.core.UiTestCore}) retargets those calls — wherever they
 * happen — at {@link LwjglKeyboardSeam}, which answers from here while {@link #isControlling()} is
 * true and forwards to the real LWJGL method otherwise. That keeps real input working whenever the
 * harness is idle, and it is the 1.12.2 spelling of the
 * {@code KeyboardHandlerMixin}/{@code ScreenModifierMixin} pair.
 *
 * <p>An event is one {@code Keyboard.next()} iteration: a key code, the character it produced and
 * whether it went down or up. Vanilla's {@code GuiScreen.handleKeyboardInput} reads exactly those
 * three, so a queued event is indistinguishable from a real keystroke.
 *
 * <p>Threads: the queue and the held-key set are touched by the client thread only, but the set is
 * a concurrent one because the harness may be armed from a command thread.
 */
public final class KeyState {

    private static volatile boolean controlling;

    private static final Set<Integer> down =
            Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());

    private static final ConcurrentLinkedQueue<KeyEvent> queue = new ConcurrentLinkedQueue<KeyEvent>();

    private static int currentKey;
    private static char currentChar;
    private static boolean currentState;

    private KeyState() {}

    /** Whether the harness owns keyboard input right now. Read on every vanilla key poll. */
    public static boolean isControlling() {
        return controlling;
    }

    /** Hands keyboard input to the harness (or back to the real device). */
    public static void setControlling(boolean value) {
        controlling = value;
        if (!value) clear();
    }

    /** Empties the queue and forgets every held key. */
    public static void clear() {
        queue.clear();
        down.clear();
        currentKey = 0;
        currentChar = 0;
        currentState = false;
    }

    /** Presses or releases a key without queueing an event (the held-key view). */
    public static void setDown(int key, boolean value) {
        if (value) down.add(key);
        else down.remove(key);
    }

    /** Whether the harness believes that key is held. */
    public static boolean isDown(int key) {
        return down.contains(key);
    }

    /** Queues one key event for {@code Keyboard.next()} to deliver. */
    public static void enqueue(int key, char character, boolean state) {
        queue.add(new KeyEvent(key, character, state));
    }

    /** Queues a press and a release of the same key, in that order. */
    public static void tap(int key, char character) {
        enqueue(key, character, true);
        enqueue(key, character, false);
    }

    /**
     * Queues the characters of a string, one character event each.
     *
     * <p>A character is <em>one</em> event, not a press and a release: LWJGL 2 reports typing as an
     * event with {@code getEventKey() == KEY_NONE} and a non-zero {@code getEventCharacter()}, which
     * is exactly the shape {@code GuiScreen.handleKeyboardInput} tests for
     * ({@code getEventKey() == 0 && c0 >= ' '}). Queuing a press/release pair per character would
     * type every character twice.
     */
    public static void type(String text) {
        for (int i = 0; i < text.length(); i++) {
            enqueue(0, text.charAt(i), true);
        }
    }

    /** Advances to the next queued event; false when the queue is empty. */
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

    /** The queued events, for the report: what the harness still has to deliver. */
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
