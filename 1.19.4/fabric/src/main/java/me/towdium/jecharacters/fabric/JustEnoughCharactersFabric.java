package me.towdium.jecharacters.fabric;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.JustEnoughCharacters;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

@SuppressWarnings("unused")
public class JustEnoughCharactersFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        JustEnoughCharacters.init();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
                JustEnoughCharacters.registerCommand(dispatcher, ClientCommandManager::literal));
        JechConfig.reload = ModConfigImpl::reload;
    }
}
