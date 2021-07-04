package me.towdium.jecharacters;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;
import net.minecraftforge.fml.loading.FMLLoader;
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
        if (ModList.get().isLoaded("i18nupdatemod")) {
            logger.info("Good to see you, TartaricAcid and your friends.");
        }
        if (ModList.get().isLoaded("fruittrees") || ModList.get().isLoaded("snowrealmagic")) {
            logger.info("Good to see you, Snownee.");
        }
    }

    @SuppressWarnings("resource")
    public static void printMessage(ITextComponent message) {
        Minecraft.getInstance().ingameGUI.getChatGUI().printChatMessage(message);
    }

    @Mod.EventBusSubscriber
    static class EventHandler {
        @SubscribeEvent
        public static void onPlayerLogin(EntityJoinWorldEvent event) {
            if (event.getEntity() instanceof PlayerEntity && event.getEntity().world.isRemote
                    && JechConfig.enableChat.get() && !messageSent
                    && (JechConfig.enumKeyboard.get() == QUANPIN)
                    && Minecraft.getInstance().gameSettings.language.equals("zh_tw")) {
                printMessage(new TranslationTextComponent("jecharacters.chat.taiwan"));
                messageSent = true;
            }
        }
    }
}

