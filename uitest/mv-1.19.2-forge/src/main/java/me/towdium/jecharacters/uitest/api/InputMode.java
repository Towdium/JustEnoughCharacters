package me.towdium.jecharacters.uitest.api;

/**
 * How scripted input reaches the game.
 *
 * <p>Constants are lowerCamelCase because this repository's style gate rejects UPPER_SNAKE enum
 * constants; {@link #fromString(String)} accepts the upper-case spelling the Gradle property uses.
 */
public enum InputMode {

    /** Dispatches at the live screen with a faked pointer and key state; never touches the OS cursor. */
    synthetic,

    /** Moves the real OS cursor with {@code glfwSetCursorPos}; needs a focused, visible window. */
    real;

    /** Reads either spelling; anything unrecognised means {@link #synthetic}. */
    public static InputMode fromString(String raw) {
        return "real".equalsIgnoreCase(raw.trim()) ? real : synthetic;
    }
}
