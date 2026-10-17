package me.towdium.jecharacters.uitest.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import org.jspecify.annotations.Nullable;

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
        return InputConstants.isKeyDown(keyCode);
    }

    public static boolean isShiftDown() {
        return isKeyDown(Keys.translate(Keys.leftShift)) || isKeyDown(Keys.translate(Keys.rightShift));
    }

    public static boolean isCtrlDown() {
        if (isKeyDown(Keys.translate(Keys.leftControl)) || isKeyDown(Keys.translate(Keys.rightControl))) {
            return true;
        }
        return InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY
                && (isKeyDown(Keys.translate(Keys.leftSuper)) || isKeyDown(Keys.translate(Keys.rightSuper)));
    }

    public static boolean isAltDown() {
        return isKeyDown(Keys.translate(Keys.leftAlt)) || isKeyDown(Keys.translate(Keys.rightAlt));
    }

    public static boolean isSuperDown() {
        return isKeyDown(Keys.translate(Keys.leftSuper)) || isKeyDown(Keys.translate(Keys.rightSuper));
    }

    public static boolean isCommandChord() {
        return isCtrlDown();
    }

    public static boolean isCut(int keyCode) {
        return keyCode == Keys.translate(Keys.x) && isCtrlDown();
    }

    public static boolean isPaste(int keyCode) {
        return keyCode == Keys.translate(Keys.v) && isCtrlDown();
    }

    public static boolean isCopy(int keyCode) {
        return keyCode == Keys.translate(Keys.c) && isCtrlDown();
    }

    public static boolean isSelectAll(int keyCode) {
        return keyCode == Keys.translate(Keys.a) && isCtrlDown();
    }
}
