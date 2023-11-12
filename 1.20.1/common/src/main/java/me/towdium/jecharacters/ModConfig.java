package me.towdium.jecharacters;

import dev.architectury.injectables.annotations.ExpectPlatform;

public class ModConfig {

    @ExpectPlatform
    public static void register() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void reload() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void save() {
        throw new AssertionError();
    }
}
