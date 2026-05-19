package me.towdium.jecharacters;

import com.google.auto.service.AutoService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

@AutoService(CommandRegister.class)
public class ModernCommandRegister implements CommandRegister {

    @SuppressWarnings("unchecked")
    @Override
    public void register(LiteralArgumentBuilder<?> builder) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register((LiteralArgumentBuilder<FabricClientCommandSource>) builder));
    }
}
