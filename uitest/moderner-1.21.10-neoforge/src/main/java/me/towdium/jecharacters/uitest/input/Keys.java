package me.towdium.jecharacters.uitest.input;

import org.lwjgl.glfw.GLFW;

/**
 * Key helpers shared by the scripted gestures: the GLFW scan code a key code maps to, the
 * modifier mask a held key contributes, and the printable-character range.
 */
public final class Keys {

    /** GLFW modifier bits, as passed to {@code Screen.keyPressed}. */
    public static final int shiftMask = 0x0001;

    public static final int controlMask = 0x0002;

    public static final int altMask = 0x0004;

    public static final int superMask = 0x0008;

    private Keys() {}

    /** The platform scan code for a GLFW key code; {@code 0} when the key has none. */
    public static int scanCodeOf(int keyCode) {
        int scanCode = GLFW.glfwGetKeyScancode(keyCode);
        return Math.max(scanCode, 0);
    }

    /**
     * Whether a code point is a printable character a {@code charTyped} step should deliver:
     * anything from the space code point up, minus the delete control character.
     */
    public static boolean isPrintable(int codePoint) {
        return codePoint >= 32 && codePoint != 127;
    }

    public static boolean isModifier(int keyCode) {
        return modifierMaskOf(keyCode) != 0;
    }

    /** The GLFW modifier bit a key code contributes, or {@code 0} when it is not a modifier. */
    public static int modifierMaskOf(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> shiftMask;
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> controlMask;
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> altMask;
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> superMask;
            default -> 0;
        };
    }

    /** Whether the key code is one a text field accepts as content. */
    public static boolean isTextKey(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_APOSTROPHE && keyCode <= GLFW.GLFW_KEY_GRAVE_ACCENT) {
            return true;
        }
        return keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_EQUAL;
    }
}
