package me.towdium.jecharacters.fabric;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.JustEnoughCharacters;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;

@SuppressWarnings("unused")
public class JustEnoughCharactersFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        JustEnoughCharacters.init(ClientCommandManager.getActiveDispatcher(), ClientCommandManager::literal);
        JechConfig.reload = ModConfigImpl::reload;
    }
}
