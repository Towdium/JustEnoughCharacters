package me.towdium.jecharacters;

import me.towdium.jecharacters.utils.Greetings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class JustEnoughCharacters implements ClientModInitializer {
    public static final String MODID = "jecharacters";
    public static Logger logger = LogManager.getLogger(MODID);
    static boolean messageSent = false;

    public static void printMessage(Component message) {
        Minecraft.getInstance().gui.getChat().addMessage(message);
    }

    @Override
    public void onInitializeClient() {
        Greetings.send(logger, MODID);
        JechConfig.register();
        JechConfig.loadConfig();
        ClientCommandManager.DISPATCHER.register(JechCommand.builder);
    }

    /*
    static class EventHandler {
        @SubscribeEvent
        public static void onPlayerLogin(EntityJoinWorldEvent event) {
            if (event.getEntity() instanceof Player && event.getEntity().level.isClientSide
                    && JechConfig.enableChat.get() && !messageSent
                    && (JechConfig.enumKeyboard.get() == QUANPIN)
                    && Minecraft.getInstance().options.languageCode.equals("zh_tw")) {
                printMessage(new TranslatableComponent("jecharacters.chat.taiwan"));
                messageSent = true;
            }
        }
    }

     */
}

