package me.towdium.jecharacters.uitest.input;

import me.towdium.jecharacters.mixin.uitest.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The "the run is a player in the world" seam: while it is live, the harness owns the client's
 * mouse-look and in-world key state instead of a screen.
 *
 * <p>Two vanilla behaviours have to be overridden for scripted in-world input to be possible at
 * all, and both are keyed off this one flag:
 *
 * <ol>
 *   <li><b>The window must count as active.</b> {@code MouseHandler.handleAccumulatedMovement}
 *       — the per-frame pump that turns accumulated mouse deltas into {@code player.turn()} — is
 *       wrapped in {@code if (minecraft.isWindowActive())} ({@code MouseHandler.java:266}), and
 *       {@code onMove} only accumulates while the window is active too ({@code :251}). A dev run
 *       whose window is unfocused (the user switched app, or a hidden multi-process client) would
 *       silently stop turning. {@code MinecraftMixin} answers {@code isWindowActive()} with
 *       {@code true} while this seam is live, exactly as a real player's focused window would.</li>
 *   <li><b>The mouse must count as grabbed.</b> The turning half of the same method is additionally
 *       gated on {@code isMouseGrabbed()} ({@code MouseHandler.java:289}). The harness never calls
 *       {@code grabMouse()}: that method warps the real pointer to the window centre and asks GLFW
 *       to capture the cursor ({@code MouseHandler.java:369-385}), which is a side effect on the
 *       user's machine that a test has no business performing. {@link #takeLook} sets the flag
 *       directly instead, and {@code MouseHandlerMixin} cancels {@code grabMouse} while an
 *       embodiment is live so nothing else grabs the pointer on its way through
 *       {@code Minecraft.setScreen(null)}.</li>
 * </ol>
 *
 * <p>The consequence of 2 is the one fidelity divergence worth stating: the OS cursor is never
 * captured, so the GLFW cursor mode stays {@code NORMAL} for the whole run. It cannot be observed by
 * the game — {@code RawInputGate} drops every real move before it is accumulated — and it is what
 * keeps the real pointer usable while the script drives the camera.
 *
 * <p>Opening a screen releases the look ({@link #releaseLook}, called from
 * {@code Minecraft.setScreen}), because with {@code mouseGrabbed} still set vanilla would keep
 * turning the player while a screen is up — a state a real client cannot reach, since
 * {@code setScreen} calls {@code releaseMouse()} first.
 */
public final class PlayerEmbodiment {

    private static final Logger logger = LoggerFactory.getLogger("jechtest/uitest");

    private static volatile @Nullable Object owner;

    private PlayerEmbodiment() {}

    /** Takes the in-world seam on behalf of {@code newOwner}, replacing any previous owner. */
    public static void activate(Object newOwner) {
        owner = newOwner;
    }

    /** Releases the seam only when {@code currentOwner} is the one holding it. */
    public static void deactivate(Object currentOwner) {
        if (owner == currentOwner) {
            owner = null;
        }
    }

    /** Whether a scripted player is in the world right now. */
    public static boolean isLive() {
        return owner != null;
    }

    /** Whether {@code candidate} is the embodiment currently holding the seam. */
    public static boolean isOwnedBy(Object candidate) {
        return owner == candidate;
    }

    /**
     * Puts the client into the same mouse-look state a real player has: {@code mouseGrabbed} set,
     * the OS cursor left alone. Without it {@code handleAccumulatedMovement} never reaches
     * {@code turnPlayer}.
     */
    public static void takeLook() {
        MouseHandler handler = Minecraft.getInstance().mouseHandler;
        if (handler == null || handler.isMouseGrabbed()) {
            return;
        }
        if (!(handler instanceof MouseHandlerAccessor accessor)) {
            logger.warn("[uitest] MouseHandlerAccessor is not applied; the camera cannot turn");
            return;
        }
        accessor.setMouseGrabbed(true);
    }

    /**
     * Drops the look without {@code releaseMouse}'s pointer warp, so a screen can open during a live
     * run exactly as it does for a real player — with the camera no longer following the mouse.
     */
    public static void releaseLook() {
        if (!isLive()) {
            return;
        }
        MouseHandler handler = Minecraft.getInstance().mouseHandler;
        if (handler == null || !handler.isMouseGrabbed()) {
            return;
        }
        if (handler instanceof MouseHandlerAccessor accessor) {
            accessor.setMouseGrabbed(false);
        }
    }
}
