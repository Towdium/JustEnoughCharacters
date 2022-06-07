package me.towdium.jecharacters;

import me.towdium.jecharacters.utils.Greetings;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.ParametersAreNonnullByDefault;

import static me.towdium.jecharacters.JechConfig.Spell.QUANPIN;
import static net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD;


@Mod.EventBusSubscriber(bus = MOD)
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod(JustEnoughCharacters.MODID)
public class JustEnoughCharacters {
    public static final String MODID = "jecharacters";
    public static Logger logger = LogManager.getLogger(MODID);
    static boolean messageSent = false;

    public JustEnoughCharacters() {
        JechConfig.register();
    }

    @SubscribeEvent
    public static void onConstruct(FMLConstructModEvent event) {
        Greetings.send(logger, MODID);
    }

    @SuppressWarnings("resource")
    public static void printMessage(Component message) {
        Minecraft.getInstance().gui.getChat().addMessage(message);
    }

    @Mod.EventBusSubscriber
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
}

