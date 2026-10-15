package me.towdium.jecharacters.uitest.input;

import org.lwjgl.input.Keyboard;

/**
 * The keyboard half of the harness's input seam: one static method per {@code
 * org.lwjgl.input.Keyboard} read that the game performs, with the very same descriptor.
 *
 * <p>The coremod retargets every such read in the game's own classes at these methods — {@code
 * Keyboard.next()} inside {@code GuiScreen.handleInput} becomes {@code LwjglKeyboardSeam.next()},
 * and so on — so the call site, its descriptor and its exception table all stay as vanilla wrote
 * them and only the callee changes. Each method forwards to the real LWJGL read whenever {@link
 * #controls()} is false: before a run starts, between scenarios, and after the harness hands input
 * back, a human keeps full control of the client.
 *
 * <p><b>Port note.</b> On 1.21.1 the same behaviour is six Mixins into vanilla's own wrapper
 * classes ({@code KeyboardHandlerMixin}, {@code MouseHandlerMixin}, {@code InputConstantsMixin},
 * {@code ScreenModifierMixin}, {@code MinecraftMixin} and the two accessors). 1.12.2 has no such
 * wrapper — vanilla calls {@code org.lwjgl.input.Keyboard} directly — so the seam retargets those
 * reads where they happen instead. It cannot rewrite {@code Keyboard} itself: launchwrapper's
 * {@code LaunchClassLoader} excludes {@code org.lwjgl.} from both its own definition and its
 * transformer chain, so no coremod ever sees that class (see
 * {@link me.towdium.jecharacters.uitest.seam.Seams} for the evidence). The observable contract is
 * unchanged, and this end of it is closer to the 1.21.1 design than a LWJGL rewrite would have
 * been.
 */
public final class LwjglKeyboardSeam {

    private LwjglKeyboardSeam() {}

    /** Whether the harness owns keyboard input: the guard every read below tests. */
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

    /** The harness never repeats an event, so an event it produced is never a repeat. */
    public static boolean isRepeatEvent() {
        return !KeyState.isControlling() && Keyboard.isRepeatEvent();
    }

    public static long getEventNanoseconds() {
        return KeyState.isControlling() ? System.nanoTime() : Keyboard.getEventNanoseconds();
    }
}
