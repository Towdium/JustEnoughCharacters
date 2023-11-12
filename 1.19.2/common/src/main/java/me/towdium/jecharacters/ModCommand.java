package me.towdium.jecharacters;

import dev.architectury.injectables.annotations.ExpectPlatform;

public class ModCommand {

    @ExpectPlatform
    public static int setKeyboard(JechConfig.Spell keyboard) {
        throw new AssertionError();
    }
}
