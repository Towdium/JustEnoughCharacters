package me.towdium.jecharacters;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.ParametersAreNonnullByDefault;

import static me.towdium.jecharacters.JechConfig.Spell.QUANPIN;

@Mod.EventBusSubscriber
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
    public static void onPlayerLogin(EntityJoinWorldEvent event) {
        if (event.getEntity() instanceof PlayerEntity && event.getEntity().world.isRemote
                && JechConfig.enableChat.get() && !messageSent
                && (JechConfig.enumKeyboard.get() == QUANPIN)
                && Minecraft.getInstance().gameSettings.language.equals("zh_tw")) {
            event.getEntity().sendMessage(new TranslationTextComponent("jecharacters.chat.taiwan"));
            messageSent = true;
        }
    }
}

