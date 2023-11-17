package me.towdium.jecharacters.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.fml.ModList;

public class PlatformUtilsImpl {
    public static void sendMessage(String message) {
        Minecraft.getInstance().gui.getChat().addMessage(new TranslatableComponent(message));
    }

    public static boolean isModLoaded(String modid) {
        return ModList.get().isLoaded(modid);
    }
}