package me.towdium.jecharacters.uitest;

import me.towdium.jecharacters.uitest.platform.FabricPlatform;
import me.towdium.jecharacters.uitest.platform.Platform;
import net.fabricmc.api.ClientModInitializer;

public final class UiTest implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Platform.register(new FabricPlatform());
    }
}
