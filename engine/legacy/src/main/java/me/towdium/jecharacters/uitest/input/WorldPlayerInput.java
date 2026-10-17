package me.towdium.jecharacters.uitest.input;

import me.towdium.jecharacters.mixin.uitest.KeyboardHandlerAccessor;
import me.towdium.jecharacters.mixin.uitest.MouseHandlerAccessor;
import me.towdium.jecharacters.uitest.api.CameraFrame;
import me.towdium.jecharacters.uitest.api.PlayerInput;
import me.towdium.jecharacters.uitest.platform.InputPlatform;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class WorldPlayerInput implements PlayerInput {

    private static final Logger logger = LogManager.getLogger("jechtest/uitest");

    private static final double entityTurnScale = 0.15;

    private final SyntheticInputDriver driver;

    private final IntSet heldKeys = new IntOpenHashSet();

    private final Map<Object, Integer> heldBinds = new LinkedHashMap<>();

    private final Set<Object> directBinds = new LinkedHashSet<>();

    private float sweepStartYaw;
    private float sweepStartPitch;
    private float sweepDeltaYaw;
    private float sweepDeltaPitch;
    private int sweepDurationTicks;
    private double sweepStartClock;
    private boolean sweepActive;

    private long clientTick;

    private @Nullable Boolean smoothCameraBefore;

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
    public void pressBind(Function bind) {
        Object mapping = require(bind);
        int key = input().pressableKeyOf(mapping);
        if (key < 0) {
            logger.warn(
                "[uitest] bind {} carries no pressable key (bound to {}); the press cannot go "
                    + "through the real path",
                input().bindName(mapping), input().bindKey(mapping)
            );
            return;
        }
        int[] modifiers = input().modifierKeysOf(mapping);
        int mask = modifierMaskOf(modifiers);
        for (int modifier : modifiers) {
            holdKey(modifier);
        }
        try {
            if (input().mouseBind(mapping)) {
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
    public void holdBind(Function bind) {
        Object mapping = require(bind);
        int key = input().pressableKeyOf(mapping);
        if (key < 0) {
            logger.warn(
                "[uitest] bind {} carries no pressable key (bound to {}); holding its own state instead",
                input().bindName(mapping), input().bindKey(mapping)
            );
            directBinds.add(mapping);
            input().setBindDown(mapping, true);
            return;
        }
        if (input().mouseBind(mapping)) {
            heldBinds.put(mapping, key);
            mouseDown(key);
            return;
        }
        heldBinds.put(mapping, key);
        holdKey(key);
    }

    @Override
    public void releaseBind(Function bind) {
        Object mapping = require(bind);
        if (directBinds.remove(mapping)) {
            input().setBindDown(mapping, false);
            return;
        }
        Integer key = heldBinds.remove(mapping);
        if (key == null) {
            return;
        }
        if (input().mouseBind(mapping)) {
            mouseUp(key);
        } else {
            releaseKey(key);
        }
    }

    @Override
    public void hotbarSlot(int slot) {
        int count = Minecraft.getInstance().options.keyHotbarSlots.length;
        if (slot < 0 || slot >= count) {
            throw new IllegalStateException("hotbar slot " + slot + " is outside 0.." + (count - 1));
        }
        pressBind(input().hotbarSlotBind(slot));
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

    private void key(int glfwKey, int action, int modifiers) {
        requirePlayer();
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

    private static Object require(Function bind) {
        Object mapping = bind.apply(input().options());
        if (mapping == null) {
            throw new IllegalStateException("a bind lookup returned null");
        }
        return mapping;
    }

    private static int modifierMaskOf(int[] keys) {
        int mask = 0;
        for (int key : keys) {
            mask |= Keys.modifierMaskOf(key);
        }
        return mask;
    }

    // endregion

    // region the camera

    @Override
    public void turnBy(float dYaw, float dPitch) {
        requirePlayer();
        requireWorldLook();
        MouseHandlerAccessor mouse = mouse();
        int invert = input().invertYMouse() ? -1 : 1;
        double scale = turnScale() * entityTurnScale;
        double pitch = clampPitch(dPitch);
        mouse.setAccumulatedDx(mouse.getAccumulatedDx() + (dYaw / scale));
        mouse.setAccumulatedDy(mouse.getAccumulatedDy() + (pitch / (invert * scale)));
    }

    @Override
    public void lookSweep(float toYaw, float toPitch, int durationTicks) {
        requirePlayer();
        requireWorldLook();
        sweepStartYaw = input().playerYaw();
        sweepStartPitch = input().playerPitch();
        sweepDeltaYaw = input().wrapDegrees(toYaw - sweepStartYaw);
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
        lookSweep(toYaw, toPitch, Math.max(1, frames));
    }

    @Override
    public void pumpTick() {
        clientTick++;
    }

    @Override
    public void pumpFrame() {
        if (!input().worldLoaded()) {
            return;
        }
        sampleCameraFrame();
        if (!sweepActive) {
            return;
        }
        float progress = sweepProgress();
        if (progress <= 0.0F) {
            return;
        }
        if (progress >= 1.0F) {
            turnBy(
                (float) input().wrapDegrees((sweepStartYaw + sweepDeltaYaw) - input().playerYaw()),
                (float) ((sweepStartPitch + sweepDeltaPitch) - input().playerPitch())
            );
            sweepActive = false;
        } else {
            float targetYaw = sweepStartYaw + (float) (sweepDeltaYaw * progress);
            float targetPitch = sweepStartPitch + (float) (sweepDeltaPitch * progress);
            turnBy(
                (float) input().wrapDegrees(targetYaw - input().playerYaw()),
                targetPitch - input().playerPitch()
            );
        }
    }

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

    private double sweepClock() {
        return clientTick + Minecraft.getInstance().getFrameTime();
    }

    @Override
    public boolean sweeping() {
        return sweepActive;
    }

    @Override
    public List<CameraFrame> cameraTrace() {
        return Collections.unmodifiableList(new ArrayList<>(cameraTrace));
    }

    @Override
    public long positionCorrections() {
        return PositionCorrections.count();
    }

    private void sampleCameraFrame() {
        if (!cameraTraceArmed || cameraTrace.size() >= cameraTraceLimit) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!input().worldLoaded()) {
            return;
        }
        float partialTick = minecraft.getFrameTime();
        cameraTrace.add(
            new CameraFrame(
                System.nanoTime(),
                clientTick,
                partialTick,
                input().playerYaw(),
                input().playerPitch(),
                input().lerp(partialTick, input().playerPrevYaw(), input().playerYaw()),
                input().lerp(partialTick, input().playerPrevPitch(), input().playerPitch()),
                input().cameraYaw(),
                input().cameraPitch()
            )
        );
    }

    @Override
    public void cancelSweep() {
        sweepActive = false;
        cameraTraceArmed = false;
    }

    private static double turnScale() {
        double base = input().turnSensitivity();
        double cubed = base * base * base;
        if (input().firstPersonScoping()) {
            return cubed;
        }
        return cubed * 8.0;
    }

    private static float clampPitch(float dPitch) {
        float pitch = input().playerPitch();
        float wanted = input().clamp(pitch + dPitch, -90.0F, 90.0F);
        return wanted - pitch;
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
        for (IntIterator iterator = heldKeys.iterator(); iterator.hasNext();) {
            held.add(iterator.nextInt());
        }
        heldKeys.clear();
        if (input().worldLoaded()) {
            for (int key : held) {
                key(key, GLFW.GLFW_RELEASE, 0);
            }
        }
        for (Object mapping : directBinds) {
            input().setBindDown(mapping, false);
        }
        directBinds.clear();
        heldBinds.clear();
        Object handler = Minecraft.getInstance().mouseHandler;
        if (handler instanceof MouseHandlerAccessor) {
            MouseHandlerAccessor mouse = (MouseHandlerAccessor) handler;
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

    private void ensureActive() {
        if (PlayerEmbodiment.isOwnedBy(this)) {
            return;
        }
        PlayerEmbodiment.activate(this);
        if (smoothCameraBefore == null) {
            smoothCameraBefore = input().smoothCamera();
            input().setSmoothCamera(false);
        }
    }

    private void restoreSmoothCamera() {
        Boolean before = smoothCameraBefore;
        if (before == null) {
            return;
        }
        smoothCameraBefore = null;
        input().setSmoothCamera(before);
    }

    private void requirePlayer() {
        if (!input().worldLoaded()) {
            throw new IllegalStateException("the embodied player input needs a loaded world with a player");
        }
    }

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

    private static InputPlatform input() {
        return InputPlatform.current();
    }

    private static MouseHandlerAccessor mouse() {
        Object handler = Minecraft.getInstance().mouseHandler;
        if (!(handler instanceof MouseHandlerAccessor)) {
            throw new IllegalStateException("MouseHandlerAccessor is not applied; the harness cannot act in the world");
        }
        return (MouseHandlerAccessor) handler;
    }

    private static KeyboardHandlerAccessor keyboard() {
        Object handler = Minecraft.getInstance().keyboardHandler;
        if (!(handler instanceof KeyboardHandlerAccessor)) {
            throw new IllegalStateException("KeyboardHandlerAccessor is not applied; text cannot be typed");
        }
        return (KeyboardHandlerAccessor) handler;
    }

    private static long window() {
        return Minecraft.getInstance().getWindow().getWindow();
    }
}
