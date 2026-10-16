package me.towdium.jecharacters.uitest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public final class UiTest implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess) -> UiTestCommands.register(dispatcher));
    }
}
