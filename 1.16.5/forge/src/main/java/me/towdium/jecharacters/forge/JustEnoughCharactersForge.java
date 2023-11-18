package me.towdium.jecharacters.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.RootCommandNode;
import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.JustEnoughCharacters;
import me.towdium.jecharacters.PlatformUtils;
import me.towdium.jecharacters.utils.ForgeInfoReader;
import me.towdium.jecharacters.utils.Profiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.searchtree.SuffixArray;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Objects;

import static net.minecraft.ChatFormatting.*;
import static net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND;

@Mod(JustEnoughCharacters.MODID)
public class JustEnoughCharactersForge {

    static boolean messageSent = false;
    private static final CommandDispatcher<SharedSuggestionProvider> dispatcher = new CommandDispatcher<>();
    private static LiteralArgumentBuilder<SharedSuggestionProvider> builder;

    public JustEnoughCharactersForge() {
        JustEnoughCharacters.init();
        builder = JustEnoughCharacters.registerCommand(dispatcher, LiteralArgumentBuilder::literal);
        Profiler.init(new ForgeInfoReader(), SuffixArray.class.getCanonicalName());
    }

    @Mod.EventBusSubscriber
    static class EventHandler {
        @SubscribeEvent
        public static void onPlayerLogin(EntityJoinWorldEvent event) {
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
        public static void onOpenGui(GuiScreenEvent.InitGuiEvent event) {
            if (event.getGui() instanceof ChatScreen) {
                RootCommandNode<SharedSuggestionProvider> root = getPlayer().connection.getCommands().getRoot();
                if (root.getChild("jech") == null) root.addChild(builder.build());
            }
        }

        @SubscribeEvent
        public static void onCommand(ClientChatEvent event) {
            CommandSourceStack cs = getPlayer().createCommandSourceStack();
            String msg = event.getMessage();
            if (msg.startsWith("/jech ") || msg.equals("/jech")) {
                event.setCanceled(true);
                Minecraft.getInstance().gui.getChat().addRecentChat(msg);

                try {
                    StringReader stringreader = new StringReader(msg);
                    if (stringreader.canRead() && stringreader.peek() == '/') stringreader.skip();
                    ParseResults<SharedSuggestionProvider> parse = dispatcher.parse(stringreader, cs);
                    dispatcher.execute(parse);
                } catch (CommandSyntaxException e) {
                    // copied and modified from net.minecraft.command.Commands
                    cs.sendFailure(ComponentUtils.fromMessage(e.getRawMessage()));
                    if (e.getInput() != null && e.getCursor() >= 0) {
                        int k = Math.min(e.getInput().length(), e.getCursor());
                        TextComponent tc1 = new TextComponent("");
                        tc1.withStyle(GRAY).withStyle(i ->
                                i.withClickEvent(new ClickEvent(SUGGEST_COMMAND, event.getMessage())));
                        if (k > 10) tc1.append("...");
                        tc1.append(e.getInput().substring(Math.max(0, k - 10), k));
                        if (k < e.getInput().length()) {
                            Component tc2 = (new TextComponent(e.getInput().substring(k)))
                                    .withStyle(RED, UNDERLINE);
                            tc1.getSiblings().add(tc2);
                        }
                        tc1.getSiblings().add((new TranslatableComponent("command.context.here"))
                                .withStyle(RED, ITALIC));
                        cs.sendFailure(tc1);
                    }
                }
            }
        }

        private static LocalPlayer getPlayer() {
            return Objects.requireNonNull(Minecraft.getInstance().player);
        }
    }
}
