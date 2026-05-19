package me.towdium.jecharacters;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.towdium.jecharacters.config.JechConfig.Spell;
import me.towdium.jecharacters.config.JechConfigAction;
import me.towdium.jecharacters.utils.Profiler;
import net.minecraft.commands.SharedSuggestionProvider;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ServiceLoader;

import static me.towdium.jecharacters.JustEnoughCharacters.printMessage;

public class JechCommand {

    @SuppressWarnings("rawtypes")
    static LiteralArgumentBuilder builder;

    @SuppressWarnings("unchecked")
    public static <T> LiteralArgumentBuilder<T> getBuilder() {
        return builder;
    }

    static {
        JechConfigAction action = ServiceLoader.load(JechConfigAction.class).findFirst().orElseThrow();

        builder = literal("jech")
                .executes((c) -> {
                    printMessage("jecharacters.chat.help");
                    return 0;
                }).then(literal("profile").executes(c -> profile()))
                .then(literal("verbose")
                        .then(literal("true").executes(c -> {
                            action.setEnableVerbose(true);
                            return 0;
                        })).then(literal("false").executes(c -> {
                            action.setEnableVerbose(false);
                            return 0;
                        }))
                ).then(literal("keyboard")
                        .then(literal("quanpin").executes(c -> action.setKeyboard(Spell.QUANPIN)))
                        .then(literal("daqian").executes(c -> action.setKeyboard(Spell.DAQIAN)))
                        .then(literal("xiaohe").executes(c -> action.setKeyboard(Spell.XIAOHE)))
                        .then(literal("ziranma").executes(c -> action.setKeyboard(Spell.ZIRANMA)))
                        .then(literal("sougou").executes(c -> action.setKeyboard(Spell.SOUGOU)))
                        .then(literal("guobiao").executes(c -> action.setKeyboard(Spell.GUOBIAO)))
                        .then(literal("microsoft").executes(c -> action.setKeyboard(Spell.MICROSOFT)))
                        .then(literal("pinyinjiajia").executes(c -> action.setKeyboard(Spell.PINYINPP)))
                        .then(literal("ziguang").executes(c -> action.setKeyboard(Spell.ZIGUANG)))
                );
    }

    private static LiteralArgumentBuilder<SharedSuggestionProvider> literal(String s) {
        return LiteralArgumentBuilder.literal(s);
    }


    private static int profile() {
        Thread t = new Thread(() -> {
            printMessage("jecharacters.chat.start");
            Profiler.Report r = Profiler.getInstance().run();
            try (FileOutputStream fos = new FileOutputStream("logs/jecharacters.txt")) {
                OutputStreamWriter osw = new OutputStreamWriter(fos);
                osw.write(new GsonBuilder().setPrettyPrinting().create().toJson(r));
                osw.flush();
                printMessage("jecharacters.chat.saved");
            } catch (IOException e) {
                printMessage("jecharacters.chat.error");
            }
        });
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
        return 0;
    }

}
