package me.towdium.jecharacters.uitest.api;

public enum StepKind {

    // region world and server
    teleportPlayer,
    giveItem,
    runCommand,
    serverTicks,
    awaitClientChunk,
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
    embodyKey,
    embodyType,
    embodyRelease,
    // endregion

    // region waiting
    waitFrames,
    waitTicks,
    waitMs,
    waitUntil,
    // endregion

    // region assertions
    check,
    checkEquals,
    checkScreen,
    checkCapture,
    // endregion

    // region structure and tooling
    log,
    action,
    screenshot,
    teardown
    // endregion
}
