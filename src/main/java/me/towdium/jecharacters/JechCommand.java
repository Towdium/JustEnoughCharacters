package me.towdium.jecharacters;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.RootCommandNode;
import me.towdium.jecharacters.JechConfig.Spell;
import me.towdium.jecharacters.utils.Match;
import me.towdium.jecharacters.utils.Profiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Objects;

import static me.towdium.jecharacters.JustEnoughCharacters.printMessage;
import static net.minecraft.ChatFormatting.*;
import static net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND;


@Mod.EventBusSubscriber({Dist.CLIENT})
public class JechCommand {
    static CommandDispatcher<SharedSuggestionProvider> dispatcher;
    static LiteralArgumentBuilder<SharedSuggestionProvider> builder;

    static {
        builder = literal("jech")
                .executes((c) -> {
                    printMessage(new TranslatableComponent("jecharacters.chat.help"));
                    return 0;
                }).then(literal("profile").executes(c -> profile()))
                .then(literal("verbose")
                        .then(literal("true").executes(c -> {
                            JechConfig.enableVerbose.set(true);
                            return 0;
                        })).then(literal("false").executes(c -> {
                            JechConfig.enableVerbose.set(false);
                            return 0;
                        }))
                ).then(literal("silent")).executes(c -> {
                    JechConfig.enableChat.set(false);
                    return 0;
                }).then(literal("keyboard")
                        .then(literal("QUANPIN").executes(c -> setKeyboard(Spell.QUANPIN)))
                        .then(literal("DAQIAN").executes(c -> setKeyboard(Spell.DAQIAN)))
                        .then(literal("XIAOHE").executes(c -> setKeyboard(Spell.XIAOHE)))
                        .then(literal("ZIRANMA").executes(c -> setKeyboard(Spell.ZIRANMA))));
        dispatcher = new CommandDispatcher<>();
        dispatcher.register(builder);
    }

    private static LiteralArgumentBuilder<SharedSuggestionProvider> literal(String s) {
        return LiteralArgumentBuilder.literal(s);
    }

    private static int setKeyboard(Spell keyboard) {
        JechConfig.enumKeyboard.set(keyboard);
        JechConfig.enableQuote.set(false);
        Match.onConfigChange();
        return 0;
    }

    private static int profile() {
        Thread t = new Thread(() -> {
            printMessage(new TranslatableComponent("jecharacters.chat.start"));
            Profiler.Report r = Profiler.run();
            try (FileOutputStream fos = new FileOutputStream("logs/jecharacters.txt")) {
                OutputStreamWriter osw = new OutputStreamWriter(fos);
                osw.write(new GsonBuilder().setPrettyPrinting().create().toJson(r));
                osw.flush();
                printMessage(new TranslatableComponent("jecharacters.chat.saved"));
            } catch (IOException e) {
                printMessage(new TranslatableComponent("jecharacters.chat.error"));
            }
        });
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
        return 0;
    }

    @SubscribeEvent
    public static void onOpenGui(ScreenEvent.InitScreenEvent event) {
        if (event.getScreen() instanceof ChatScreen) {
            RootCommandNode<SharedSuggestionProvider> root = getPlayer().connection.getCommands().getRoot();
            if (root.getChild("jech") == null) root.addChild(builder.build());
        }
    }

    @SubscribeEvent
    @SuppressWarnings("resource")
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

    @SuppressWarnings("resource")
    private static LocalPlayer getPlayer() {
        return Objects.requireNonNull(Minecraft.getInstance().player);
    }
}
