package me.towdium.jecharacters.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TranslatableComponent;

public class PlatformUtilsImpl {

    public static void sendMessage(String message) {
        Minecraft.getInstance().gui.getChat().addMessage(new TranslatableComponent(message));
    }

    public static boolean isModLoaded(String modid) {
        return FabricLoader.getInstance().isModLoaded(modid);
    }
}
