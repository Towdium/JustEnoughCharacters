package me.towdium.jecharacters.uitest.input;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The raw-input gate: while an owner is registered, vanilla's own mouse and keyboard handlers
 * drop every event at HEAD.
 *
 * <p>Vanilla {@code MouseHandler.onMove/onPress/onScroll} and
 * {@code KeyboardHandler.keyPress/charTyped} carry no window-focus guard, so a stray click on a
 * backgrounded game window is delivered straight into the middle of a run. The gate keeps that
 * from happening. It is deliberately separate from {@link CursorState}: a scripted playback can
 * own the synthetic pointer while a watching human still gets to abort the run with the real
 * keyboard.
 *
 * <p>The window close button is unaffected — it arrives through {@code glfwWindowShouldClose}.
 *
 * <h2>The synthetic bypass</h2>
 *
 * <p>Scripted <em>in-world</em> input cannot be dispatched at a screen, because there is none: it
 * has to go through vanilla's own handler bodies — {@code MouseHandler.onPress} for
 * {@code startAttack}/{@code startUseItem} and Nimbus's crosshair routing, and
 * {@code KeyboardHandler.keyPress} for the {@code KeyMapping} a WASD hold is read from. Those are
 * the very methods the gate cancels, and a Mixin {@code @Invoker} still runs the injected
 * callbacks, so the gate has to know which caller it is looking at.
 *
 * <p>{@link #runSynthetic} and {@link #callSynthetic} are that signal: nested inside them, the gate
 * answers {@link #blocksNow} with {@code false} and the real body runs. The depth is a counter
 * rather than a flag so a nested invocation — {@code onPress} reaching a screen's own handlers —
 * cannot re-arm the gate in the middle of one synthetic call, and every exit is a {@code finally}
 * so a throwing body cannot leave the raw input unblocked for the rest of the run.
 */
public final class RawInputGate {

    private static volatile @Nullable Object owner;

    private static volatile int syntheticDepth;

    private RawInputGate() {}

    /** Blocks raw input on behalf of {@code newOwner}, replacing any previous owner. */
    public static void block(Object newOwner) {
        owner = newOwner;
    }

    /** Unblocks only when {@code currentOwner} is the owner that blocked. */
    public static void unblock(Object currentOwner) {
        if (owner == currentOwner) {
            owner = null;
        }
    }

    public static boolean isBlocked() {
        return owner != null;
    }

    public static boolean isBlockedBy(Object candidate) {
        return owner == candidate;
    }

    /** Whether the gate drops an event right now: blocked, and not inside a synthetic call. */
    public static boolean blocksNow() {
        return owner != null && syntheticDepth == 0;
    }

    /** Whether the current call is a scripted one the gate must let through. */
    public static boolean isSynthetic() {
        return syntheticDepth > 0;
    }

    /** Runs {@code body} with the gate stepped aside, for scripted in-world input. */
    public static void runSynthetic(Runnable body) {
        syntheticDepth++;
        try {
            body.run();
        } finally {
            syntheticDepth--;
        }
    }

    /** Runs {@code body} with the gate stepped aside and returns its value. */
    public static <T> T callSynthetic(Supplier<T> body) {
        syntheticDepth++;
        try {
            return body.get();
        } finally {
            syntheticDepth--;
        }
    }
}
