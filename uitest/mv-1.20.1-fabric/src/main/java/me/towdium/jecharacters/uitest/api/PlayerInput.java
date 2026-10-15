package me.towdium.jecharacters.uitest.api;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

import java.util.List;
import java.util.function.Function;

/**
 * Real in-world player input: the camera, the movement binds, the crosshair's mouse buttons and the
 * keyboard — dispatched through vanilla's own handlers rather than at a {@code Screen}.
 *
 * <p>Every method here is one gesture, executed on the frame it is called. What a gesture needs in
 * order to be <em>real</em> is that it goes through the code the game itself runs:
 *
 * <ul>
 *   <li><b>turning</b> feeds {@code MouseHandler.accumulatedDX/DY} — the deltas {@code onMove}
 *       accumulates and {@code handleAccumulatedMovement} turns the player with once per frame — so
 *       the focus gate, the invert-Y option, the cinematic branch and {@code Entity.turn}'s own
 *       scaling all apply exactly as they do for a mouse. The rotation is therefore visible one
 *       frame after the call, not during it.</li>
 *   <li><b>keys</b> go through {@code KeyboardHandler.keyPress}, which is what writes the
 *       {@code KeyMapping} a movement bind is read from, and through {@code charTyped} for text.</li>
 *   <li><b>clicks</b> go through {@code MouseHandler.onPress}, so Nimbus's crosshair routing
 *       ({@code InputEvent.MouseButton.Pre}) sees them before vanilla's
 *       {@code startAttack}/{@code startUseItem}/{@code pickBlock} does — which is the only way a
 *       click can reach a pointed in-world panel.</li>
 * </ul>
 *
 * <p>Movement is a <em>hold</em>, not a tap: {@code holdBind} leaves the bind down and the player
 * walks, jumps or sneaks on the client's own tick until {@code releaseBind}. Vanilla releases every
 * bind when a screen opens ({@code Minecraft.setScreen} → {@code KeyMapping.releaseAll()}), so a hold
 * does not survive opening the chat or the inventory — the same is true for a human player.
 *
 * <p>Everything a run holds is released by {@link #releaseAll()}, which the DSL arranges as scenario
 * teardown: a leaked hold would keep walking through the next scenario.
 *
 * <p>Calls throw {@link IllegalStateException} when there is no world to be in — no client player, or
 * a screen in the way of the camera — because a silently ignored gesture is a scenario that passes
 * for the wrong reason.
 */
public interface PlayerInput {

    /** Presses and releases a key through the real handler. */
    void pressKey(int glfwKey);

    /** Presses and releases a key with a modifier mask, e.g. {@code GLFW_MOD_SHIFT}. */
    void pressKey(int glfwKey, int modifiers);

    /** Presses a key and leaves it down until {@link #releaseKey}. */
    void holdKey(int glfwKey);

    /** Releases a key held by {@link #holdKey}. */
    void releaseKey(int glfwKey);

    /** Whether this input is holding {@code glfwKey} right now. */
    boolean isHeld(int glfwKey);

    /**
     * Presses and releases the key a bind currently carries, resolved through {@code Options} so a
     * rebound client still works. This is the form a scenario should use for the game's own binds.
     */
    void pressBind(Function<Options, KeyMapping> bind);

    /** Holds a bind down: the player walks, jumps or sneaks until {@link #releaseBind}. */
    void holdBind(Function<Options, KeyMapping> bind);

    /** Releases a bind held by {@link #holdBind}. */
    void releaseBind(Function<Options, KeyMapping> bind);

    /** Selects a hotbar slot by pressing its keybind, as a digit press would. */
    void hotbarSlot(int slot);

    /** Types a string through the real {@code charTyped} handler, one character per call. */
    void typeText(String text);

    /**
     * Turns the player by a delta, in degrees, through the real mouse-look path.
     *
     * <p>The delta is expressed as the rotation change it should produce, and the raw pointer delta
     * that produces it is derived from the live sensitivity: the call is the inverse of
     * {@code MouseHandler.turnPlayer} + {@code Entity.turn}, so the player's rotation moves by
     * {@code dYaw}/{@code dPitch} on the next frame (to float precision). Pitch is clamped the way
     * {@code Entity.turn} clamps it, so the reported delta is what actually happens.
     */
    void turnBy(float dYaw, float dPitch);

    /**
     * Starts a smooth look sweep to an absolute pose, spread over {@code durationTicks} game ticks.
     * Yaw takes the short way round.
     *
     * <p>The sweep is defined on the game's own clock, not on frames: it spans {@code durationTicks}
     * client ticks, so its wall-clock duration is the same whatever the frame rate is doing, and the
     * per-tick increment is the span divided by {@code durationTicks}. Each rendered frame writes the
     * delta that lands the camera on the clock's interpolated target ({@code ticksElapsed +
     * partialTick}), through the real mouse-look path, so the rendered trace advances every frame at a
     * rate set by the game's clock rather than by however fast frames happen to arrive. The last write
     * lands exactly on the target.
     *
     * <p>The previous frame-counted form is {@link #lookSweepFrames}; it is deprecated because a
     * frame is not a unit of time — at uncapped frame rates the same sweep covers a different span of
     * seconds from run to run.
     */
    void lookSweep(float toYaw, float toPitch, int durationTicks);

    /**
     * The frame-counted sweep, kept for source compatibility and converted honestly: a frame is read
     * as one twentieth of a second (the tick rate), so {@code frames} frames become {@code frames}
     * ticks. A caller that meant "40 frames at whatever the frame rate is" gets two seconds instead —
     * which is the point of the conversion, not an accident.
     *
     * @deprecated use {@link #lookSweep(float, float, int)}, which names a duration instead of a
     *             frame count.
     */
    @Deprecated
    void lookSweepFrames(float toYaw, float toPitch, int frames);

    /**
     * Advances the run's client-tick counter by one. The run's tick hook calls it once per client
     * tick — the game's own clock, which is the unit {@link #lookSweep} is measured in — so a sweep
     * knows how many ticks have passed without asking the level (whose game time is synced from the
     * server and is not the client's own tick count).
     */
    void pumpTick();

    /**
     * Advances the active sweep by one rendered frame, writing this frame's share of the turn through
     * the real mouse-look path. The run's frame hook calls it once per frame, before anything else in
     * the frame does.
     *
     * <p>The share is not a fixed fraction of the span per frame: it is whatever lands the camera on
     * the sweep's target for the game clock's <em>current</em> position, so the drawn angle advances
     * every frame at a wall-clock rate the clock sets rather than the frame rate. The last write lands
     * on the target exactly, which is what keeps a frame the sweep could not fully deliver from
     * leaving a residue behind.
     */
    void pumpFrame();

    /**
     * Every rendered frame since the last sweep was armed, in render order — the angle the engine
     * drew that frame with, the raw and interpolated rotations behind it, and its wall-clock stamp.
     *
     * <p>This is the smoothness seam. A scenario asserts on {@link CameraFrame#cameraYaw()} — what the
     * renderer posed — and not on the raw entity rotation, because the two can come apart and only the
     * first is what a viewer sees.
     */
    List<CameraFrame> cameraTrace();

    /**
     * Every position correction this client has received this run: {@code ClientboundPlayerPositionPacket},
     * the packet a server answers a client with when it does not agree with where the client says it is.
     *
     * <p>Monotonic and read from the client thread, so a scenario walking under a scripted camera can
     * tell whether a stutter in the trace is the input path's or the server re-seating the player's.
     */
    long positionCorrections();

    /** Whether a sweep is in progress. */
    boolean sweeping();

    /** Drops an unfinished sweep. */
    void cancelSweep();

    /** Left click through the real handler: mines a block, or lands on a pointed panel. */
    void attack();

    /** Right click through the real handler: uses the block or item under the crosshair. */
    void useItem();

    /** Middle click through the real handler. */
    void pickBlock();

    /** Presses a mouse button and leaves it down. */
    void mouseDown(int button);

    /** Releases a mouse button. */
    void mouseUp(int button);

    /** Scrolls a notch under the crosshair (positive is up). */
    void scroll(double amount);

    /** Releases every key, bind and sweep this input still holds, and gives the seam back. */
    void releaseAll();

    /** Whether this input currently owns the in-world seam. */
    boolean active();
}
