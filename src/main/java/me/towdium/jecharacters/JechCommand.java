package me.towdium.jecharacters;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.towdium.jecharacters.JechConfig.Spell;
import me.towdium.jecharacters.utils.Match;
import me.towdium.jecharacters.utils.Profiler;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.network.chat.TranslatableComponent;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import static me.towdium.jecharacters.JustEnoughCharacters.printMessage;


public class JechCommand {
    static LiteralArgumentBuilder<FabricClientCommandSource> builder;

    static {
        builder = literal("jech")
                .executes((c) -> {
                    printMessage(new TranslatableComponent("jecharacters.chat.help"));
                    return 0;
                }).then(literal("profile").executes(c -> profile()))
                .then(literal("verbose")
                        .then(literal("true").executes(c -> {
                            JechConfig.setEnableVerbose(true);
                            return 0;
                        })).then(literal("false").executes(c -> {
                            JechConfig.setEnableVerbose(false);
                            return 0;
                        }))
                ).then(literal("keyboard")
                        .then(literal("quanpin").executes(c -> setKeyboard(Spell.QUANPIN)))
                        .then(literal("daqian").executes(c -> setKeyboard(Spell.DAQIAN)))
                        .then(literal("xiaohe").executes(c -> setKeyboard(Spell.XIAOHE)))
                        .then(literal("ziranma").executes(c -> setKeyboard(Spell.ZIRANMA))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String s) {
        return LiteralArgumentBuilder.literal(s);
    }

    private static int setKeyboard(Spell keyboard) {
        JechConfig.setKeyboard(keyboard);
        JechConfig.setEnableQuote(false);
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
}
