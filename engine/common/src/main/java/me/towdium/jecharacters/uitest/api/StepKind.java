package me.towdium.jecharacters.uitest.api;

public enum StepKind {

    // region world and server
    setBlock,
    fill,
    clearArea,
    withBlockEntity,
    teleportPlayer,
    yawPitch,
    giveItem,
    runCommand,
    serverTicks,
    awaitClientChunk,
    awaitClientBlockEntity,
    // endregion

    // region screens
    openScreen,
    awaitScreen,
    closeScreen,
    // endregion

    // region input
    hover,
    click,
    rightClick,
    shiftClick,
    doubleClick,
    clickAt,
    scroll,
    drag,
    key,
    keyDown,
    keyUp,
    type,
    typeInto,
    embodyTurn,
    embodyKey,
    embodyType,
    embodyClick,
    embodyRelease,
    // endregion

    // region waiting
    waitFrames,
    waitTicks,
    waitMs,
    waitUntil,
    waitUntilServer,
    // endregion

    // region assertions
    check,
    checkEquals,
    checkScreen,
    checkCapture,
    // endregion

    // region structure and tooling
    group,
    repeat,
    log,
    action,
    screenshot,
    screenshotSurface,
    screenshotSequence,
    timelineRecord,
    teardown,
    teardownServer
    // endregion
}
