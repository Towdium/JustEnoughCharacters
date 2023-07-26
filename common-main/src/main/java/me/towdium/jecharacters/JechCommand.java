package me.towdium.jecharacters;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.towdium.jecharacters.JechConfig.Spell;
import me.towdium.jecharacters.utils.Match;
import me.towdium.jecharacters.utils.Profiler;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.function.Consumer;
import java.util.function.Function;


public class JechCommand {


    public static <S> void register(
            Function<String, LiteralArgumentBuilder<S>> literal,
            CommandDispatcher<S> dispatcher,
            Consumer<String> messageSender
    ) {
        dispatcher.register(
                literal.apply("jech")
                        .executes((c) -> {
                            messageSender.accept("jecharacters.chat.help");
                            return 0;
                        })
                        .then(literal.apply("profile").executes(c -> profile(messageSender)))
                        .then(literal.apply("verbose")
                                .then(literal.apply("true").executes(c -> {
                                    JechConfig.enableVerbose = true;
                                    return 0;
                                })).then(literal.apply("false").executes(c -> {
                                    JechConfig.enableVerbose = false;
                                    return 0;
                                })))
                        .then(literal.apply("silent").executes(c -> {
                            JechConfig.enableChat = false;
                            return 0;
                        }))
                        .then(literal.apply("keyboard")
                                .then(literal.apply("quanpin").executes(c -> setKeyboard(Spell.QUANPIN)))
                                .then(literal.apply("daqian").executes(c -> setKeyboard(Spell.DAQIAN)))
                                .then(literal.apply("xiaohe").executes(c -> setKeyboard(Spell.XIAOHE)))
                                .then(literal.apply("ziranma").executes(c -> setKeyboard(Spell.ZIRANMA)))
                                .then(literal.apply("sougou").executes(c -> setKeyboard(Spell.SOUGOU)))
                                .then(literal.apply("guobiao").executes(c -> setKeyboard(Spell.GUOBIAO)))
                                .then(literal.apply("microsoft").executes(c -> setKeyboard(Spell.MICROSOFT)))
                                .then(literal.apply("pinyinjiajia").executes(c -> setKeyboard(Spell.PINYINPP)))
                                .then(literal.apply("ziguang").executes(c -> setKeyboard(Spell.ZIGUANG))))
        );
    }


    private static int setKeyboard(Spell keyboard) {
        JechConfig.enumKeyboard = keyboard;
        JechConfig.enableQuote = false;
        Match.onConfigChange();
        return 0;
    }

    private static int profile(Consumer<String> messageSender) {
        Thread t = new Thread(() -> {
            messageSender.accept("jecharacters.chat.start");
            Profiler.Report r = Profiler.getInstance().run();
            try (FileOutputStream fos = new FileOutputStream("logs/jecharacters.txt")) {
                OutputStreamWriter osw = new OutputStreamWriter(fos);
                osw.write(new GsonBuilder().setPrettyPrinting().create().toJson(r));
                osw.flush();
                messageSender.accept("jecharacters.chat.saved");
            } catch (IOException e) {
                messageSender.accept("jecharacters.chat.error");
            }
        });
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
        return 0;
    }

}
