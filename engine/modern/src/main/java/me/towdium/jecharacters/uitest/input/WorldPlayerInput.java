package me.towdium.jecharacters.uitest.input;

import com.mojang.blaze3d.platform.InputConstants;
import me.towdium.jecharacters.mixin.uitest.KeyboardHandlerAccessor;
import me.towdium.jecharacters.mixin.uitest.MouseHandlerAccessor;
import me.towdium.jecharacters.uitest.api.CameraFrame;
import me.towdium.jecharacters.uitest.api.PlayerInput;
import me.towdium.jecharacters.uitest.platform.ClientPlatform;
import me.towdium.jecharacters.uitest.platform.InputPlatform;
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
import org.jspecify.annotations.Nullable;
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

    private final SyntheticInputDriver driver;

    private final IntSet heldKeys = new IntOpenHashSet();

    private final Map<KeyMapping, Integer> heldBinds = new LinkedHashMap<>();

    private final Set<KeyMapping> directBinds = new LinkedHashSet<>();

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
        int[] held = Keys.modifierKeysOf(modifiers);
        for (int modifier : held) {
            holdKey(modifier);
        }
        try {
            key(glfwKey, Keys.press, modifiers);
            key(glfwKey, Keys.release, modifiers);
        } finally {
            for (int i = held.length - 1; i >= 0; i--) {
                releaseKey(held[i]);
            }
        }
    }

    @Override
    public void holdKey(int glfwKey) {
        key(glfwKey, Keys.press, 0);
    }

    @Override
    public void releaseKey(int glfwKey) {
        key(glfwKey, Keys.release, 0);
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
            logger.warn(
                "[uitest] bind {} carries no pressable key (bound to {}); the press cannot go "
                    + "through the real path",
                mapping.getName(), input().keyOf(mapping)
            );
            return;
        }
        int[] modifiers = input().modifierKeysOf(mapping);
        int mask = modifierMaskOf(modifiers);
        for (int modifier : modifiers) {
            holdKey(modifier);
        }
        try {
            if (InputConstants.Type.MOUSE == input().keyOf(mapping).getType()) {
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
            logger.warn(
                "[uitest] bind {} carries no pressable key (bound to {}); holding its own state instead",
                mapping.getName(), input().keyOf(mapping)
            );
            directBinds.add(mapping);
            mapping.setDown(true);
            return;
        }
        if (InputConstants.Type.MOUSE == input().keyOf(mapping).getType()) {
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
        if (InputConstants.Type.MOUSE == input().keyOf(mapping).getType()) {
            mouseUp(key);
        } else {
            releaseKey(key);
        }
    }

    @Override
    public void typeText(String text) {
        requirePlayer();
        ensureActive();
        long window = window();
        text.codePoints().forEach(codePoint -> {
            if (Keys.isPrintable(codePoint)) {
                RawInputGate.runSynthetic(() -> input().typeChar(keyboard(), window, codePoint));
            }
        });
    }

    private void key(int glfwKey, int action, int modifiers) {
        requirePlayer();
        ensureActive();
        long window = window();
        int scanCode = Keys.scanCodeOf(glfwKey);
        boolean pressed = action == Keys.press;
        RawInputGate.runSynthetic(
            () -> input().pressKey(keyboard(), window, glfwKey, scanCode, action, modifiers)
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
        InputConstants.Key key = input().keyOf(mapping);
        if (key.getType() == InputConstants.Type.MOUSE) {
            return key.getValue();
        }
        if (key.getName().startsWith("key.keyboard.") && key.getValue() != InputConstants.UNKNOWN.getValue()) {
            return key.getValue();
        }
        return -1;
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
        float partialTick = input().partialTick();
        cameraTrace.add(
            new CameraFrame(
                System.nanoTime(),
                clientTick,
                partialTick,
                player.getYRot(),
                player.getXRot(),
                Mth.lerp(partialTick, player.yRotO, player.getYRot()),
                Mth.lerp(partialTick, player.xRotO, player.getXRot()),
                input().cameraYaw(),
                input().cameraPitch()
            )
        );
    }

    @Override
    public void cancelSweep() {
        cameraTraceArmed = false;
    }

    // endregion

    // region the mouse buttons

    private void mouseDown(int button) {
        requirePlayer();
        requireWorldLook();
        long window = window();
        RawInputGate.runSynthetic(() -> input().pressMouse(mouse(), window, button, Keys.press));
    }

    private void mouseUp(int button) {
        requirePlayer();
        requireWorldLook();
        long window = window();
        RawInputGate.runSynthetic(() -> input().pressMouse(mouse(), window, button, Keys.release));
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
                key(key, Keys.release, 0);
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
        if (ClientPlatform.current().screen() != null) {
            throw new IllegalStateException(
                "the embodied player input needs the world, but "
                        + ClientPlatform.current().screen().getClass().getSimpleName() + " is open"
            );
        }
        ensureActive();
        PlayerEmbodiment.takeLook();
    }

    private static InputPlatform input() {
        return InputPlatform.current();
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
        return input().window();
    }
}
