package me.towdium.jecharacters.uitest.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class KeyState {

    @FunctionalInterface
    public interface Source {

        boolean isKeyDown(int keyCode);
    }

    private static volatile @Nullable Source source;

    private KeyState() {}

    public static void setSource(Source newSource) {
        source = newSource;
    }

    public static void clearSource(Source owner) {
        if (source == owner) {
            source = null;
        }
    }

    public static @Nullable Source source() {
        return source;
    }

    public static boolean isInstalled() {
        return source != null;
    }

    public static boolean isKeyDown(int keyCode) {
        Source current = source;
        if (current != null) {
            return current.isKeyDown(keyCode);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), keyCode);
    }

    public static boolean isShiftDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean isCtrlDown() {
        if (isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL)) {
            return true;
        }
        return Minecraft.ON_OSX && (isKeyDown(GLFW.GLFW_KEY_LEFT_SUPER) || isKeyDown(GLFW.GLFW_KEY_RIGHT_SUPER));
    }

    public static boolean isAltDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_ALT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_ALT);
    }

    public static boolean isSuperDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_SUPER) || isKeyDown(GLFW.GLFW_KEY_RIGHT_SUPER);
    }

    public static boolean isCommandChord() {
        return isCtrlDown();
    }

    public static boolean isCut(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_X && isCtrlDown();
    }

    public static boolean isPaste(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_V && isCtrlDown();
    }

    public static boolean isCopy(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_C && isCtrlDown();
    }

    public static boolean isSelectAll(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_A && isCtrlDown();
    }
}
