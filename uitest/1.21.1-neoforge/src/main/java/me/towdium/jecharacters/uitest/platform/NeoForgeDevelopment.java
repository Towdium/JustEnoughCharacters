package me.towdium.jecharacters.uitest.platform;

import net.neoforged.fml.loading.FMLEnvironment;

public final class NeoForgeDevelopment {

    private NeoForgeDevelopment() {}

    static boolean isDevelopment() {
        return !FMLEnvironment.production;
    }
}
