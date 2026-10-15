package me.towdium.jecharacters.uitest.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The held-key override seam.
 *
 * <p>Modifier state is not part of any input event payload: CloudLib reads
 * {@code Screen.hasControlDown()} and friends, which resolve to
 * {@code InputConstants.isKeyDown(window, GLFW_KEY_*)} — a GLFW query against the live keyboard.
 * A scripted gesture therefore cannot express "Ctrl-click" by event arguments alone; it needs
 * this seam (or real keys held down). The clipboard chords are reimplemented here for the same
 * reason: vanilla's {@code Screen.isCopy} and friends read the main window handle directly, so
 * they would ignore an installed source.
 */
public final class KeyState {

    /**
     * The override provider: whether a given GLFW key code is currently held.
     */
    @FunctionalInterface
    public interface Source {

        boolean isKeyDown(int keyCode);
    }

    private static volatile @Nullable Source source;

    private KeyState() {}

    /** Installs {@code newSource}, replacing any previous one. */
    public static void setSource(Source newSource) {
        source = newSource;
    }

    /** Clears the source only when {@code owner} is the one currently installed. */
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

    /**
     * Whether {@code keyCode} is held: through the installed source when there is one, otherwise
     * through GLFW, exactly like vanilla {@code Screen.hasControlDown} does.
     */
    public static boolean isKeyDown(int keyCode) {
        Source current = source;
        if (current != null) {
            return current.isKeyDown(keyCode);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        return InputConstants.isKeyDown(minecraft.getWindow(), keyCode);
    }

    public static boolean isShiftDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    /**
     * Control — mirroring vanilla, Cmd counts as control on macOS.
     *
     * <p>The "which key is the edit-shortcut key" half is vanilla's own
     * {@link InputQuirks#REPLACE_CTRL_KEY_WITH_CMD_KEY}, which is what replaced the removed
     * {@code Minecraft.ON_OSX} (MC 1.21.10; the same constant also drives
     * {@link InputQuirks#EDIT_SHORTCUT_KEY_MODIFIER}, the modifier bit a modern
     * {@code InputWithModifiers} reads).
     */
    public static boolean isCtrlDown() {
        if (isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL)) {
            return true;
        }
        return InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY
                && (isKeyDown(GLFW.GLFW_KEY_LEFT_SUPER) || isKeyDown(GLFW.GLFW_KEY_RIGHT_SUPER));
    }

    public static boolean isAltDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_ALT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_ALT);
    }

    public static boolean isSuperDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_SUPER) || isKeyDown(GLFW.GLFW_KEY_RIGHT_SUPER);
    }

    /** Whether the platform command modifier for a text chord is down. */
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
