package me.towdium.jecharacters;

import dev.architectury.injectables.annotations.ExpectPlatform;

public class PlatformUtils {

    @ExpectPlatform
    public static void sendMessage(String message) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isModLoaded(String modid) {
        return false;
    }

}
