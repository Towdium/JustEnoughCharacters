package me.towdium.jecharacters;

import me.towdium.jecharacters.config.JechConfig;
import me.towdium.jecharacters.utils.Greetings;
import me.towdium.jecharacters.utils.Profiler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import static me.towdium.jecharacters.JustEnoughCharacters.MODID;
import static me.towdium.jecharacters.JustEnoughCharacters.logger;
import static me.towdium.jecharacters.JustEnoughCharacters.printMessage;
import static me.towdium.jecharacters.JustEnoughCharacters.suffixClassName;
import static me.towdium.jecharacters.config.JechConfig.Spell.QUANPIN;


@Mod(value = JustEnoughCharacters.MODID, dist = Dist.CLIENT)
public class JustEnoughCharactersForge {
    static boolean messageSent = false;

    public JustEnoughCharactersForge(IEventBus modBus, ModContainer modContainer) {
        modBus.register(this);
        JechConfigForge.register(modBus, modContainer);
        Profiler.init(suffixClassName);
    }

    @SubscribeEvent
    public void onConstruct(FMLConstructModEvent event) {
        Greetings.send(logger, MODID, id -> ModList.get().isLoaded(id));
    }

    @EventBusSubscriber(value = Dist.CLIENT)
    static class EventHandler {
        @SubscribeEvent
        public static void onPlayerLogin(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof Player &&
                event.getLevel().isClientSide() &&
                    JechConfig.enableChat && !messageSent &&
                    (JechConfig.enumKeyboard == QUANPIN) &&
                    "zh_tw".equals(Minecraft.getInstance().options.languageCode)) {
                printMessage("jecharacters.chat.taiwan");
                messageSent = true;
            }
        }

        @SubscribeEvent
        public static void onClientCommandRegister(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(JechCommand.getBuilder());
        }

    }
}

