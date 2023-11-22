package me.towdium.jecharacters.forge;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.towdium.jecharacters.Constants;
import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.JustEnoughCharacters;
import me.towdium.jecharacters.PlatformUtils;
import me.towdium.jecharacters.utils.ForgeInfoReader;
import me.towdium.jecharacters.utils.Profiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.searchtree.SuffixArray;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class JustEnoughCharactersForge {

    static boolean messageSent = false;

    public JustEnoughCharactersForge() {
        JustEnoughCharacters.init();
        Profiler.init(new ForgeInfoReader(), SuffixArray.class.getCanonicalName());
    }

    @Mod.EventBusSubscriber
    static class EventHandler {
        @SubscribeEvent
        public static void onPlayerLogin(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof Player
                    && event.getEntity().level.isClientSide
                    && JechConfig.enableChat
                    && !messageSent
                    && (JechConfig.enumKeyboard == JechConfig.Spell.QUANPIN)
                    && Minecraft.getInstance().options.languageCode.equals("zh_tw")) {
                PlatformUtils.sendMessage("jecharacters.chat.taiwan");
                messageSent = true;
            }
        }

        @SubscribeEvent
        public static void onClientCommandRegister(RegisterClientCommandsEvent event) {
            JustEnoughCharacters.registerCommand(event.getDispatcher(), LiteralArgumentBuilder::literal);
        }
    }
}
