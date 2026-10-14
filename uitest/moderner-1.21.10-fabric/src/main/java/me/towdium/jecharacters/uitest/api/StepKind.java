package me.towdium.jecharacters.uitest.api;

/**
 * What a step does. Carried on the step report so a run's shape is readable without replaying it.
 *
 * <p>Constants are lowerCamelCase, matching the upstream CloudLib harness this engine is ported from.
 *
 * <p><b>Port note.</b> The kinds that describe a CloudLib widget-tree assertion
 * ({@code checkExists}, {@code checkNotExists}, {@code checkCount}, {@code checkVisible},
 * {@code checkFocused}, {@code checkHovered}, {@code checkClass}, {@code checkBounds},
 * {@code checkTree}, {@code checkGoldenImage}, {@code screenshotElement}), the widget-level focus
 * gestures ({@code focus}, {@code blur}) and the multi-process kinds ({@code announce},
 * {@code barrier}, {@code sync}, {@code fetch}) are gone with the parts of the API they belonged to.
 */
public enum StepKind {

    // region world and server
    /** Place one block on the integrated server. */
    setBlock,
    /** Fill a box with one block. */
    fill,
    /** Replace a box with air. */
    clearArea,
    /** Mutate a block entity on the server thread. */
    withBlockEntity,
    /** Move the local player. */
    teleportPlayer,
    /** Aim the local player. */
    yawPitch,
    /** Put an item in the local player's inventory. */
    giveItem,
    /** Run a server command. */
    runCommand,
    /** Let the integrated server tick a fixed number of times. */
    serverTicks,
    /** Wait for a client chunk to load. */
    awaitClientChunk,
    /** Wait for a client block entity to appear. */
    awaitClientBlockEntity,
    // endregion

    // region screens
    /** Show a screen. */
    openScreen,
    /** Wait for a screen of a given type to be shown. */
    awaitScreen,
    /** Close whatever screen is shown. */
    closeScreen,
    // endregion

    // region input
    /** Move the scripted pointer to a point in GUI-scaled space. */
    hover,
    /** Left click at a point. */
    click,
    /** Right click at a point. */
    rightClick,
    /** Click at a point with shift held. */
    shiftClick,
    /** Click at a point twice inside the double-click threshold. */
    doubleClick,
    /** Click at raw coordinates. */
    clickAt,
    /** Scroll at a point. */
    scroll,
    /** Drag from one point to another. */
    drag,
    /** Press and release a key with modifiers held. */
    key,
    /** Press a key and leave it down. */
    keyDown,
    /** Release a key. */
    keyUp,
    /** Type a string one character per step. */
    type,
    /** Replace the focused text field's content. */
    typeInto,
    /** Turn the player through the real mouse-look path. */
    embodyTurn,
    /** Press, hold or release a key or a bind through the real keyboard handler. */
    embodyKey,
    /** Type a character through the real keyboard handler. */
    embodyType,
    /** Click a mouse button through the real handler. */
    embodyClick,
    /** Release every hold the embodied player input carries. */
    embodyRelease,
    // endregion

    // region waiting
    /** Wait a number of rendered frames. */
    waitFrames,
    /** Wait a number of client ticks. */
    waitTicks,
    /** Wait a wall-clock time. */
    waitMs,
    /** Wait for a client-side condition. */
    waitUntil,
    /** Wait for a condition evaluated on the integrated server thread. */
    waitUntilServer,
    // endregion

    // region assertions
    /** A named boolean assertion. */
    check,
    /** An equality assertion. */
    checkEquals,
    /** The shown screen is an instance of a type. */
    checkScreen,
    /** A capture with the given label has been written to disk. */
    checkCapture,
    // endregion

    // region structure and tooling
    /** A label for the steps that follow. */
    group,
    /** A step that only names a repeated block. */
    repeat,
    /** Write a line into the step log. */
    log,
    /** Runs an action on the client without recording an assertion. */
    action,
    /** Capture the full frame. */
    screenshot,
    /** Capture an arbitrary colour surface. */
    screenshotSurface,
    /** Capture a labelled run of consecutive frames. */
    screenshotSequence,
    /** Append a record to the scenario's timeline log. */
    timelineRecord,
    /** Cleanup that runs on the client after the scenario's steps. */
    teardown,
    /** Cleanup that runs on the integrated server after the scenario's steps. */
    teardownServer
    // endregion
}
