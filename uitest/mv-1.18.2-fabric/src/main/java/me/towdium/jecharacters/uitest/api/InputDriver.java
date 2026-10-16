package me.towdium.jecharacters.uitest.api;

import net.minecraft.client.gui.screens.Screen;

/**
 * Injects scripted pointer and keyboard input into the live screen.
 *
 * <p>Everything is dispatched at the {@link Screen} the game currently shows, in GUI-scaled screen
 * space, never at a widget tree directly: a container screen's {@code mouseClicked} is what runs the
 * slot-click path, and CloudLib's own scenes take their hover from the screen's move callback.
 *
 * <p>The default bodies do nothing, so a driver only overrides the entry points its route supports.
 * An implementation that fakes pointer or key state must undo that in {@link #uninstall()} even when
 * a step threw: a leaked override leaves the real mouse and keyboard dead.
 */
public abstract class InputDriver {

    /**
     * Where the pointer parks between runs. Not {@code 0,0}, which is the top-left corner of a
     * maximised window and would hover whatever sits there on the first frame.
     */
    public static final float parked = -10_000f;

    /** Takes over the pointer and key state; safe to call once per run. */
    public abstract void install();

    /** Releases everything {@link #install()} took; must be exception-safe. */
    public abstract void uninstall();

    /**
     * Whether {@link #install()} currently holds the seams. A run logs this right after
     * {@link #uninstall()}: a leaked cursor or key-state source would leave the player without a
     * working mouse for the rest of the session, so the false has to be visible.
     */
    public abstract boolean installed();

    /** Whether this driver drives a stand-in pointer rather than the OS cursor. */
    public abstract boolean usesVirtualCursor();

    /** Moves the scripted pointer and refreshes hover before the next event is dispatched. */
    public abstract void placeCursor(float xGui, float yGui);

    /** Move only. */
    public void moveTo(Screen screen, float x, float y) {}

    /** Press a button at a position. */
    public void mouseDown(Screen screen, float x, float y, int button) {}

    /** Release a button at a position. */
    public void mouseUp(Screen screen, float x, float y, int button) {}

    /** Move while a button is held, as the screen's drag callback expects. */
    public void dragTo(Screen screen, float x, float y, int button, float deltaX, float deltaY) {}

    /**
     * Press a button at a position through the client's own mouse handler instead of at the screen.
     *
     * <p>{@link #mouseDown} hands the event straight to the screen, which is what a vanilla widget
     * reads. A mod's overlay that is <em>not</em> a widget of the open screen never sees that: JEI's
     * search bar, say, is a field its overlay owns and feeds itself, so the click it hears is one
     * dispatched through the body a real pointer goes through — the pointer half of the "handler
     * route" the keyboard steps ({@code pressKey}, {@code typeText}) already take.
     *
     * <p>Both halves of a click are needed: the press is what an overlay's input handler is offered
     * first, the release is where one that accepted the press applies it.
     */
    public void mouseDownThroughHandler(Screen screen, float x, float y, int button) {}

    /** Release a button at a position through the client's own mouse handler. */
    public void mouseUpThroughHandler(Screen screen, float x, float y, int button) {}

    /** Scroll at a position. */
    public void scroll(Screen screen, float x, float y, double amount) {}

    /** Press a key at a position, with the modifier mask the event carries. */
    public void keyDown(Screen screen, int keyCode, int scanCode, int modifiers) {}

    /** Release a key. */
    public void keyUp(Screen screen, int keyCode, int scanCode, int modifiers) {}

    /** Type one character. */
    public void charTyped(Screen screen, int codePoint, int modifiers) {}

    /** The held-state seam: whether a key counts as down right now. */
    public abstract boolean isKeyDown(int keyCode);

    /**
     * The in-world player surface: the camera, the movement binds and the crosshair's buttons.
     *
     * <p>It is separate from the screen gestures above because it is a different host. A screen
     * gesture is dispatched at the live {@code Screen}; an in-world gesture is dispatched at
     * vanilla's own {@code MouseHandler}/{@code KeyboardHandler} bodies, with no screen anywhere —
     * which is the only way a click can reach a pointed in-world panel or a WASD hold can move the
     * player. A driver that has no such surface returns an implementation that refuses, never one
     * that silently does nothing.
     */
    public abstract PlayerInput playerInput();
}
