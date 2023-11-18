package me.towdium.jecharacters.fabric;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.JustEnoughCharacters;
import me.towdium.jecharacters.utils.FabricInfoReader;
import me.towdium.jecharacters.utils.Profiler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.minecraft.client.searchtree.SuffixArray;

@SuppressWarnings("unused")
public class JustEnoughCharactersFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        JustEnoughCharacters.init();
        JustEnoughCharacters.registerCommand(ClientCommandManager.DISPATCHER, ClientCommandManager::literal);
        JechConfig.reload = ModConfigImpl::reload;
        Profiler.init(new FabricInfoReader(), SuffixArray.class.getCanonicalName());
    }
}
