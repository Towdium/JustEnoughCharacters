package me.towdium.jecharacters.uitest.api;

/**
 * What a step does — the label the report carries for it.
 *
 * <p><b>Port note.</b> This is the 1.12.2 subset of the 1.21.1 enum of the same name, grown by the
 * wave that brought the JSON vocabulary to this line. The kinds whose machinery this checkout does
 * not carry are still gone rather than stubbed: the element-capture kinds ({@code captureElement},
 * {@code captureSequence}, {@code captureSurface}) belong to the CloudLib widget tree and the
 * sequence pipeline, the in-world embodiment kinds ({@code holdBind}/{@code releaseBind}/{@code
 * lookAt}) belong to {@code PlayerEmbodiment}, and the world/server kinds this line folds into one
 * seam ({@code setBlock}, {@code fill}, {@code yawPitch}, …) are absent because there is no
 * per-step server thread for them. What this wave added is exactly the kinds the 22-step JSON
 * vocabulary needs: the coordinate gesture ({@code click}), the modifier-bearing key gesture
 * ({@code key}, {@code pressKey}), {@code closeScreen}, the wall-clock wait {@code waitMs}, and the
 * three server-command verbs ({@code teleportPlayer}, {@code giveItem}, {@code runCommand}).
 */
public enum StepKind {

    /** A free-form body. */
    action,

    /** Records a log line. */
    log,

    /** Records an assertion. */
    check,

    /** Asserts a previously taken capture is on disk and not a flat colour. */
    checkCaptured,

    /** Takes a whole-framebuffer screenshot. */
    screenshot,

    /** Types a string one character event at a time through the keyboard seam. */
    type,

    /** Queues a key-down event through the keyboard seam. */
    keyDown,

    /** Queues a key-up event through the keyboard seam. */
    keyUp,

    /** Presses a keybind's key as a tap and hands it to the game's own bind processing. */
    pressBind,

    /** Presses and releases a key as a tap through the keyboard seam. */
    pressKey,

    /** Presses and releases a key as a tap through the keyboard seam, with the screen routing. */
    key,

    /** Clicks a point in GUI-scaled coordinates through the pointer seam. */
    click,

    /** Closes whatever screen is shown. */
    closeScreen,

    /** Waits for the client to show a screen of the named class. */
    awaitScreen,

    /** Asserts the client shows a screen of the named class. */
    checkScreen,

    /** Waits a number of rendered frames. */
    waitFrames,

    /** Waits a number of client ticks. */
    waitTicks,

    /** Waits a wall-clock time. */
    waitMs,

    /** Waits until a predicate holds. */
    waitUntil,

    /** Waits the world's own tick clock. On this line the integrated server ticks with the client. */
    serverTicks,

    /** Moves the local player, through a server command. */
    teleportPlayer,

    /** Puts an item into the local player's inventory, through a server command. */
    giveItem,

    /** Runs a server command. */
    runCommand,

    /** The scenario's own teardown. */
    teardown;

    /**
     * Whether this step is expected to repeat itself rather than finish on the first attempt.
     *
     * <p>Two shapes repeat on this line: the waits, and the steps whose work leaves the client thread
     * or the seam's queue and is only finished when the game consumes it — a queued keystroke, a
     * queued click, a command submitted to the server thread.
     */
    public boolean waits() {
        switch (this) {
            case type:
            case keyDown:
            case keyUp:
            case pressBind:
            case pressKey:
            case key:
            case click:
            case awaitScreen:
            case waitFrames:
            case waitTicks:
            case waitMs:
            case waitUntil:
            case serverTicks:
            case teleportPlayer:
            case giveItem:
            case runCommand:
                return true;
            default:
                return false;
        }
    }
}
