package me.towdium.jecharacters.uitest.api;

public enum StepKind {

    action,

    log,

    check,

    checkCaptured,

    screenshot,

    type,

    keyDown,

    keyUp,

    pressBind,

    pressKey,

    key,

    click,

    closeScreen,

    awaitScreen,

    checkScreen,

    waitFrames,

    waitTicks,

    waitMs,

    waitUntil,

    serverTicks,

    teleportPlayer,

    giveItem,

    runCommand,

    teardown;

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
