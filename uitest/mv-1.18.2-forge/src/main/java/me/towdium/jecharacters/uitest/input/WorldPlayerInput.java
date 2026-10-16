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
import net.minecraftforge.client.settings.KeyModifier;
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

final class WorldPlayerInput implements PlayerInput {

    private static final Logger logger = LoggerFactory.getLogger("jechtest/uitest");

    private static final double entityTurnScale = 0.15;

    private final SyntheticInputDriver driver;

    private final IntSet heldKeys = new IntOpenHashSet();

    private final Map<KeyMapping, Integer> heldBinds = new LinkedHashMap<>();

    private final Set<KeyMapping> directBinds = new LinkedHashSet<>();

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
    public void pressBind(Function<Options, KeyMapping> bind) {
        KeyMapping mapping = require(bind);
        int key = pressableKeyOf(mapping);
        if (key < 0) {
            logger.warn("[uitest] bind {} is unbound; the press cannot go through the real path", mapping.getName());
            return;
        }
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

    private static final int[] MODIFIER_KEY_CANDIDATES = {
            GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL,
            GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT,
            GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT,
            GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER,
    };

    private static int[] modifierKeysOf(KeyMapping mapping) {
        KeyModifier modifier = mapping.getKeyModifier();
        if (modifier == null) return new int[0];
        List<Integer> keys = new ArrayList<>(2);
        for (int keyCode : MODIFIER_KEY_CANDIDATES) {
            InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(keyCode);
            if (modifier.matches(key) && keyCode != InputConstants.UNKNOWN.getValue()) {
                keys.add(keyCode);
            }
        }
        int[] result = new int[keys.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = keys.get(i);
        }
        return result;
    }

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
        int invert = Minecraft.getInstance().options.invertYMouse ? -1 : 1;
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
        return List.copyOf(cameraTrace);
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
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        float partialTick = minecraft.getFrameTime();
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

    private static double turnScale() {
        Options options = Minecraft.getInstance().options;
        if (options.smoothCamera) {
            throw new IllegalStateException("the cinematic camera is on; an exact scripted turn needs it off");
        }
        double base = options.sensitivity * 0.6 + 0.2;
        double cubed = base * base * base;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && Minecraft.getInstance().options.getCameraType().isFirstPerson() && player.isScoping()) {
            return cubed;
        }
        return cubed * 8.0;
    }

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

    private LocalPlayer requirePlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            throw new IllegalStateException("the embodied player input needs a loaded world with a player");
        }
        return player;
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
