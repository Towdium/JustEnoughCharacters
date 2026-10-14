package me.towdium.jecharacters.uitest.input;

import com.mojang.blaze3d.platform.InputConstants;
import me.towdium.jecharacters.mixin.uitest.KeyboardHandlerAccessor;
import me.towdium.jecharacters.mixin.uitest.MouseHandlerAccessor;
import me.towdium.jecharacters.uitest.api.CameraFrame;
import me.towdium.jecharacters.uitest.api.PlayerInput;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.extensions.IKeyMappingExtension;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The in-world input driver: every gesture in {@link PlayerInput}, dispatched through vanilla's own
 * handlers with {@link RawInputGate} stepped aside.
 *
 * <p>Two pieces of arithmetic deserve to be stated here, because the whole turn API is built on them.
 *
 * <p><b>Turning.</b> A frame's turn is {@code MouseHandler.turnPlayer}, which reads the accumulated
 * pointer deltas and calls {@code player.turn(d0, d1 * invert)} where
 * {@code d0 = accumulatedDX * d4}, and {@code Entity.turn} then applies a further {@code 0.15} to both
 * axes. {@code d4} is {@code (sensitivity * 0.6 + 0.2)^3 * 8} — the non-cinematic, non-scoping branch
 * — and the cinematic branch is the one case this class refuses instead of approximating, because a
 * {@code SmoothDouble} filter has no exact inverse. {@link #turnBy} therefore inverts
 * {@code d4 * 0.15}, read from the live {@code Options} through the same NeoForge hook
 * {@code turnPlayer} consults, so a scenario asking for 10° gets 10° no matter what sensitivity the
 * client carries. The turn lands on the next frame: the pump runs before the harness's own frame
 * callback, so a delta written now is consumed at the start of the next frame and then zeroed.
 *
 * <p><b>Holding.</b> {@code KeyboardHandler.keyPress} is the only writer of the {@code KeyMapping}
 * that {@code KeyboardInput.tick} reads WASD, jump and sneak from, so a hold is a real press that is
 * simply never followed by a release. Vanilla drops every hold when a screen opens; a leak past the
 * end of a scenario is prevented by {@link #releaseAll}, which the DSL registers as teardown.
 */
final class WorldPlayerInput implements PlayerInput {

    private static final Logger logger = LoggerFactory.getLogger("jechtest/uitest");

    /** {@code Entity.turn} scales both axes by this before adding them to the rotation. */
    private static final double entityTurnScale = 0.15;

    private final SyntheticInputDriver driver;

    private final IntSet heldKeys = new IntOpenHashSet();

    /** Binds held through {@link #holdBind}, mapped to the key this input pressed for them. */
    private final Map<KeyMapping, Integer> heldBinds = new LinkedHashMap<>();

    /** Binds whose key cannot be pressed (unbound), held by writing their own state. */
    private final Set<KeyMapping> directBinds = new LinkedHashSet<>();

    private float sweepStartYaw;
    private float sweepStartPitch;
    private float sweepDeltaYaw;
    private float sweepDeltaPitch;
    private int sweepDurationTicks;
    private double sweepStartClock;
    private boolean sweepActive;

    /** The client's tick counter, advanced once per tick by the run's tick hook. */
    private long clientTick;

    private @Nullable Boolean smoothCameraBefore;

    /** Every frame's drawn camera angle while a trace is armed, capped so a long run cannot grow it. */
    private final List<CameraFrame> cameraTrace = new ArrayList<>();

    private static final int cameraTraceLimit = 20_000;

    private boolean cameraTraceArmed;

    WorldPlayerInput(SyntheticInputDriver driver) {
        this.driver = driver;
    }

    // region keys and binds

    @Override
    public void pressKey(int glfwKey) {
        pressKey(glfwKey, 0);
    }

    @Override
    public void pressKey(int glfwKey, int modifiers) {
        key(glfwKey, GLFW.GLFW_PRESS, modifiers);
        key(glfwKey, GLFW.GLFW_RELEASE, modifiers);
    }

    @Override
    public void holdKey(int glfwKey) {
        key(glfwKey, GLFW.GLFW_PRESS, 0);
    }

    @Override
    public void releaseKey(int glfwKey) {
        key(glfwKey, GLFW.GLFW_RELEASE, 0);
    }

    @Override
    public boolean isHeld(int glfwKey) {
        return heldKeys.contains(glfwKey);
    }

    @Override
    public void pressBind(Function<Options, KeyMapping> bind) {
        KeyMapping mapping = require(bind);
        int key = pressableKeyOf(mapping);
        if (key < 0) {
            // An unbound bind has no key to press, and there is no public way to make the game
            // consume a click on one. Saying so is better than clicking every UNKNOWN-keyed mapping.
            logger.warn("[uitest] bind {} is unbound; the press cannot go through the real path", mapping.getName());
            return;
        }
        // A mapping a mod declared with a modifier (NeoForge's KeyModifier, written into
        // options.txt as the ":CONTROL" suffix) only answers while that modifier counts as held.
        // "Held" is read through Screen.hasControlDown() and friends, which the harness's virtual
        // key state answers for — so the modifier is held as a real key for the press and released
        // afterwards, exactly as a player's chord would be. A vanilla bind carries NONE and this
        // loop is empty, which is why the plain binds behave as they always did.
        int[] modifiers = modifierKeysOf(mapping);
        int mask = modifierMaskOf(modifiers);
        for (int modifier : modifiers) {
            holdKey(modifier);
        }
        try {
            if (InputConstants.Type.MOUSE == mapping.getKey().getType()) {
                mouseDown(key);
                mouseUp(key);
            } else {
                pressKey(key, mask);
            }
        } finally {
            for (int i = modifiers.length - 1; i >= 0; i--) {
                releaseKey(modifiers[i]);
            }
        }
    }

    @Override
    public void holdBind(Function<Options, KeyMapping> bind) {
        KeyMapping mapping = require(bind);
        int key = pressableKeyOf(mapping);
        if (key < 0) {
            logger.warn("[uitest] bind {} is unbound; holding its own state instead", mapping.getName());
            directBinds.add(mapping);
            mapping.setDown(true);
            return;
        }
        if (InputConstants.Type.MOUSE == mapping.getKey().getType()) {
            heldBinds.put(mapping, key);
            mouseDown(key);
            return;
        }
        heldBinds.put(mapping, key);
        holdKey(key);
    }

    @Override
    public void releaseBind(Function<Options, KeyMapping> bind) {
        KeyMapping mapping = require(bind);
        if (directBinds.remove(mapping)) {
            mapping.setDown(false);
            return;
        }
        Integer key = heldBinds.remove(mapping);
        if (key == null) {
            return;
        }
        if (InputConstants.Type.MOUSE == mapping.getKey().getType()) {
            mouseUp(key);
        } else {
            releaseKey(key);
        }
    }

    @Override
    public void hotbarSlot(int slot) {
        KeyMapping[] slots = Minecraft.getInstance().options.keyHotbarSlots;
        if (slot < 0 || slot >= slots.length) {
            throw new IllegalStateException("hotbar slot " + slot + " is outside 0.." + (slots.length - 1));
        }
        pressBind(options -> options.keyHotbarSlots[slot]);
    }

    @Override
    public void typeText(String text) {
        requirePlayer();
        ensureActive();
        long window = window();
        text.codePoints().forEach(codePoint -> {
            if (Keys.isPrintable(codePoint)) {
                RawInputGate.runSynthetic(() -> keyboard().invokeCharTyped(window, codePoint, 0));
            }
        });
    }

    /** Presses or releases a key through {@code KeyboardHandler.keyPress}, the real body. */
    private void key(int glfwKey, int action, int modifiers) {
        requirePlayer();
        // a key hold is an embodiment act even with no camera involved: it is what makes the game's
        // own raw key polls answer for this key, which is how a mod binding reads a hold through a
        // capture screen that has released every key mapping
        ensureActive();
        long window = window();
        int scanCode = Keys.scanCodeOf(glfwKey);
        boolean pressed = action == GLFW.GLFW_PRESS;
        RawInputGate.runSynthetic(
            () -> Minecraft.getInstance().keyboardHandler.keyPress(window, glfwKey, scanCode, action, modifiers)
        );
        if (pressed) {
            heldKeys.add(glfwKey);
            driver.markKeyHeld(glfwKey);
        } else {
            heldKeys.remove(glfwKey);
            driver.markKeyReleased(glfwKey);
        }
    }

    /** The GLFW key a bind carries, or {@code -1} when it has none this input can press. */
    private static int pressableKeyOf(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()) {
            return key.getValue();
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return key.getValue();
        }
        return -1;
    }

    /**
     * The GLFW keys a mapping's own modifier spells.
     *
     * <p>NeoForge's {@code KeyModifier} is where a mod records "this bind needs Ctrl"; its
     * {@code codes()} answers with the keys that count (Cmd on macOS, where the platform's control
     * chord is the command key). A vanilla mapping has {@code NONE}, whose {@code codes()} is empty.
     */
    private static int[] modifierKeysOf(KeyMapping mapping) {
        if (!(mapping instanceof IKeyMappingExtension extension)) {
            return new int[0];
        }
        KeyModifier modifier = extension.getKeyModifier();
        if (modifier == null) return new int[0];
        List<Integer> keys = new ArrayList<>(2);
        for (InputConstants.Key code : modifier.codes()) {
            if (code.getType() == InputConstants.Type.KEYSYM && code.getValue() != InputConstants.UNKNOWN.getValue()) {
                keys.add(code.getValue());
            }
        }
        int[] result = new int[keys.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = keys.get(i);
        }
        return result;
    }

    /** The GLFW modifier mask a set of held modifier keys contributes. */
    private static int modifierMaskOf(int[] keys) {
        int mask = 0;
        for (int key : keys) {
            mask |= Keys.modifierMaskOf(key);
        }
        return mask;
    }

    private static KeyMapping require(Function<Options, KeyMapping> bind) {
        KeyMapping mapping = bind.apply(Minecraft.getInstance().options);
        if (mapping == null) {
            throw new IllegalStateException("a bind lookup returned null");
        }
        return mapping;
    }

    // endregion

    // region the camera

    @Override
    public void turnBy(float dYaw, float dPitch) {
        LocalPlayer player = requirePlayer();
        requireWorldLook();
        MouseHandlerAccessor mouse = mouse();
        int invert = Minecraft.getInstance().options.invertYMouse().get() ? -1 : 1;
        double scale = turnScale() * entityTurnScale;
        double pitch = clampPitch(player, dPitch);
        mouse.setAccumulatedDx(mouse.getAccumulatedDx() + (dYaw / scale));
        mouse.setAccumulatedDy(mouse.getAccumulatedDy() + (pitch / (invert * scale)));
    }

    @Override
    public void lookSweep(float toYaw, float toPitch, int durationTicks) {
        LocalPlayer player = requirePlayer();
        requireWorldLook();
        sweepStartYaw = player.getYRot();
        sweepStartPitch = player.getXRot();
        sweepDeltaYaw = Mth.wrapDegrees(toYaw - sweepStartYaw);
        sweepDeltaPitch = toPitch - sweepStartPitch;
        sweepDurationTicks = Math.max(1, durationTicks);
        sweepStartClock = sweepClock();
        sweepActive = true;
        cameraTrace.clear();
        cameraTraceArmed = true;
    }

    @Deprecated
    @Override
    public void lookSweepFrames(float toYaw, float toPitch, int frames) {
        // a frame is read as one twentieth of a second, which at the 20 tps tick rate is one tick
        lookSweep(toYaw, toPitch, Math.max(1, frames));
    }

    @Override
    public void pumpTick() {
        clientTick++;
    }

    @Override
    public void pumpFrame() {
        LocalPlayer watched = Minecraft.getInstance().player;
        if (watched == null) {
            return;
        }
        // before the write, so the sample is the angle this frame was drawn with: the write lands on
        // the next frame, and the pump runs before anything else this frame does
        sampleCameraFrame();
        if (!sweepActive) {
            return;
        }
        float progress = sweepProgress();
        if (progress <= 0.0F) {
            // the sweep was requested during this tick and its clock has not advanced yet: there is
            // nothing to write, and a zero write would draw as a stall frame at the start
            return;
        }
        if (progress >= 1.0F) {
            // the clock has run out: land the span exactly, whatever the frames before it managed
            turnBy(
                (float) Mth.wrapDegrees((sweepStartYaw + sweepDeltaYaw) - watched.getYRot()),
                (float) ((sweepStartPitch + sweepDeltaPitch) - watched.getXRot())
            );
            sweepActive = false;
        } else {
            float targetYaw = sweepStartYaw + (float) (sweepDeltaYaw * progress);
            float targetPitch = sweepStartPitch + (float) (sweepDeltaPitch * progress);
            turnBy((float) Mth.wrapDegrees(targetYaw - watched.getYRot()), targetPitch - watched.getXRot());
        }
    }

    /**
     * How far the sweep has run, in {@code 0..1}, on the game's own clock.
     *
     * <p>The clock is {@code clientTick + partialTick}: the tick counter the run advances once per
     * client tick plus the renderer's own fraction into the next tick. That is the clock the engine
     * interpolates everything else with, so a sweep paced by it takes the same wall-clock time
     * whatever the frame rate is — which is what makes the drawn trace uniform rather than tied to
     * however fast frames happen to arrive. The start is taken as the same continuous reading, not as
     * a tick number, so the sweep does not silently begin part-way through a tick.
     *
     * <p>The straight per-tick-boundary alternative was measured and rejected: the 1.21.1
     * first-person camera is not interpolated ({@code LocalPlayer.getViewYRot} answers
     * {@code getYRot()}), so an increment that only lands on a tick boundary draws as a staircase —
     * a run of zero-delta frames and then a spike. Rendering the tick clock at frame resolution keeps
     * the tick-defined duration and gives the renderer its smooth ramp.
     */
    private float sweepProgress() {
        double elapsed = sweepClock() - sweepStartClock;
        if (elapsed >= sweepDurationTicks) {
            return 1.0F;
        }
        if (elapsed <= 0.0D) {
            return 0.0F;
        }
        return (float) (elapsed / sweepDurationTicks);
    }

    /** The game clock a sweep is measured on, in ticks: the client tick plus the render fraction. */
    private double sweepClock() {
        return clientTick + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
    }

    @Override
    public boolean sweeping() {
        return sweepActive;
    }

    @Override
    public List<CameraFrame> cameraTrace() {
        return List.copyOf(cameraTrace);
    }

    @Override
    public long positionCorrections() {
        return PositionCorrections.count();
    }

    /**
     * One frame's drawn camera angle, read where the renderer left it.
     *
     * <p>The camera is posed from the player's view rotation every frame, so reading it here — after
     * this frame's render, before this frame's sweep write — is exactly what that frame drew.
     */
    private void sampleCameraFrame() {
        if (!cameraTraceArmed || cameraTrace.size() >= cameraTraceLimit) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        var camera = minecraft.gameRenderer.getMainCamera();
        cameraTrace.add(
            new CameraFrame(
                System.nanoTime(),
                clientTick,
                partialTick,
                player.getYRot(),
                player.getXRot(),
                Mth.lerp(partialTick, player.yRotO, player.getYRot()),
                Mth.lerp(partialTick, player.xRotO, player.getXRot()),
                camera.getYRot(),
                camera.getXRot()
            )
        );
    }

    @Override
    public void cancelSweep() {
        sweepActive = false;
        cameraTraceArmed = false;
    }

    /**
     * The {@code d4} factor {@code MouseHandler.turnPlayer} multiplies the raw deltas by, read from
     * the live options through the same NeoForge hook — so a mod that rewrites the sensitivity is
     * followed rather than guessed at.
     */
    private static double turnScale() {
        Options options = Minecraft.getInstance().options;
        var event = ClientHooks.getTurnPlayerValues(options.sensitivity().get(), options.smoothCamera);
        if (event.getCinematicCameraEnabled()) {
            // smoothTurnX/Y filter the delta over time: there is no delta that produces an exact
            // rotation through it, so the harness refuses rather than returning an approximation
            throw new IllegalStateException("the cinematic camera is on; an exact scripted turn needs it off");
        }
        double base = event.getMouseSensitivity() * 0.6 + 0.2;
        double cubed = base * base * base;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && Minecraft.getInstance().options.getCameraType().isFirstPerson() && player.isScoping()) {
            return cubed;
        }
        return cubed * 8.0;
    }

    /** Clamps a pitch delta the way {@code Entity.turn} clamps the result, so the delta is exact. */
    private static float clampPitch(LocalPlayer player, float dPitch) {
        float wanted = Mth.clamp(player.getXRot() + dPitch, -90.0F, 90.0F);
        return wanted - player.getXRot();
    }

    // endregion

    // region the mouse buttons

    @Override
    public void attack() {
        mouseDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        mouseUp(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    @Override
    public void useItem() {
        mouseDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        mouseUp(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }

    @Override
    public void pickBlock() {
        mouseDown(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
        mouseUp(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
    }

    @Override
    public void mouseDown(int button) {
        requirePlayer();
        requireWorldLook();
        long window = window();
        RawInputGate.runSynthetic(() -> mouse().invokeOnPress(window, button, GLFW.GLFW_PRESS, 0));
    }

    @Override
    public void mouseUp(int button) {
        requirePlayer();
        requireWorldLook();
        long window = window();
        RawInputGate.runSynthetic(() -> mouse().invokeOnPress(window, button, GLFW.GLFW_RELEASE, 0));
    }

    @Override
    public void scroll(double amount) {
        requirePlayer();
        long window = window();
        RawInputGate.runSynthetic(() -> mouse().invokeOnScroll(window, 0.0, amount));
    }

    // endregion

    @Override
    public void releaseAll() {
        cancelSweep();
        List<Integer> held = new ArrayList<>();
        for (IntIterator iterator = heldKeys.intIterator(); iterator.hasNext();) {
            held.add(iterator.nextInt());
        }
        heldKeys.clear();
        if (Minecraft.getInstance().player != null) {
            for (int key : held) {
                key(key, GLFW.GLFW_RELEASE, 0);
            }
        }
        for (KeyMapping mapping : directBinds) {
            mapping.setDown(false);
        }
        directBinds.clear();
        heldBinds.clear();
        MouseHandler handler = Minecraft.getInstance().mouseHandler;
        if (handler instanceof MouseHandlerAccessor mouse) {
            mouse.setAccumulatedDx(0.0);
            mouse.setAccumulatedDy(0.0);
        }
        restoreSmoothCamera();
        PlayerEmbodiment.releaseLook();
        PlayerEmbodiment.deactivate(this);
    }

    @Override
    public boolean active() {
        return PlayerEmbodiment.isOwnedBy(this);
    }

    /** Takes the seam on first use, and turns the cinematic camera off so a turn is exact. */
    private void ensureActive() {
        if (PlayerEmbodiment.isOwnedBy(this)) {
            return;
        }
        PlayerEmbodiment.activate(this);
        Options options = Minecraft.getInstance().options;
        if (smoothCameraBefore == null) {
            smoothCameraBefore = options.smoothCamera;
            options.smoothCamera = false;
        }
    }

    private void restoreSmoothCamera() {
        Boolean before = smoothCameraBefore;
        if (before == null) {
            return;
        }
        smoothCameraBefore = null;
        Minecraft.getInstance().options.smoothCamera = before;
    }

    /** The player must exist: an in-world gesture with no world is a scenario bug, not a no-op. */
    private LocalPlayer requirePlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            throw new IllegalStateException("the embodied player input needs a loaded world with a player");
        }
        return player;
    }

    /**
     * The camera must own the mouse: with a screen open vanilla would keep the camera still (the
     * screen holds the pointer) and a turn written now would fire at whatever closed the screen next.
     */
    private void requireWorldLook() {
        if (Minecraft.getInstance().screen != null) {
            throw new IllegalStateException(
                "the embodied player input needs the world, but "
                        + Minecraft.getInstance().screen.getClass().getSimpleName() + " is open"
            );
        }
        ensureActive();
        PlayerEmbodiment.takeLook();
    }

    private static MouseHandlerAccessor mouse() {
        MouseHandler handler = Minecraft.getInstance().mouseHandler;
        if (!(handler instanceof MouseHandlerAccessor accessor)) {
            throw new IllegalStateException("MouseHandlerAccessor is not applied; the harness cannot act in the world");
        }
        return accessor;
    }

    private static KeyboardHandlerAccessor keyboard() {
        KeyboardHandler handler = Minecraft.getInstance().keyboardHandler;
        if (!(handler instanceof KeyboardHandlerAccessor accessor)) {
            throw new IllegalStateException("KeyboardHandlerAccessor is not applied; text cannot be typed");
        }
        return accessor;
    }

    private static long window() {
        return Minecraft.getInstance().getWindow().getWindow();
    }
}
