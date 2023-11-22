package me.towdium.jecharacters.forge;

import me.towdium.jecharacters.config.JechConfigForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public class ModConfigImpl {

    public static void register() {
        JechConfigForge.register();
        FMLJavaModLoadingContext.get()
                .getModEventBus()
                .register(ModConfigImpl.class);
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent event){
        if (event.getConfig().getSpec() == JechConfigForge.common) {
            JechConfigForge.reload();
        }
    }

    public static void reload() {
        JechConfigForge.reload();
    }

    public static void save() {
        JechConfigForge.save();
    }
}
