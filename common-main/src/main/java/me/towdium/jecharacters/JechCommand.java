package me.towdium.jecharacters;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.towdium.jecharacters.JechConfig.Spell;
import me.towdium.jecharacters.utils.Profiler;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.function.Consumer;
import java.util.function.Function;

public class JechCommand {

    public static <S> LiteralArgumentBuilder<S> register(
            Function<String, LiteralArgumentBuilder<S>> literal,
            CommandDispatcher<S> dispatcher,
            Consumer<String> messageSender,
            Function<Spell, Integer> keyboardSetter,
            Runnable configSave
    ) {
        LiteralArgumentBuilder<S> builder = literal.apply("jech")
                .executes((c) -> {
                    messageSender.accept("jecharacters.chat.help");
                    return 0;
                })
                .then(literal.apply("profile").executes(c -> profile(messageSender)))
                .then(literal.apply("verbose")
                        .then(literal.apply("true").executes(c -> {
                            JechConfig.enableVerbose = true;
                            configSave.run();
                            return 0;
                        })).then(literal.apply("false").executes(c -> {
                            JechConfig.enableVerbose = false;
                            configSave.run();
                            return 0;
                        })))
                .then(literal.apply("silent").executes(c -> {
                    JechConfig.enableChat = false;
                    configSave.run();
                    return 0;
                }))
                .then(literal.apply("keyboard")
                        .then(literal.apply("quanpin").executes(c -> keyboardSetter.apply(Spell.QUANPIN)))
                        .then(literal.apply("daqian").executes(c -> keyboardSetter.apply(Spell.DAQIAN)))
                        .then(literal.apply("xiaohe").executes(c -> keyboardSetter.apply(Spell.XIAOHE)))
                        .then(literal.apply("ziranma").executes(c -> keyboardSetter.apply(Spell.ZIRANMA)))
                        .then(literal.apply("sougou").executes(c -> keyboardSetter.apply(Spell.SOUGOU)))
                        .then(literal.apply("guobiao").executes(c -> keyboardSetter.apply(Spell.GUOBIAO)))
                        .then(literal.apply("microsoft").executes(c -> keyboardSetter.apply(Spell.MICROSOFT)))
                        .then(literal.apply("pinyinjiajia").executes(c -> keyboardSetter.apply(Spell.PINYINPP)))
                        .then(literal.apply("ziguang").executes(c -> keyboardSetter.apply(Spell.ZIGUANG))));
        dispatcher.register(builder);
        return builder;
    }

    private static int profile(Consumer<String> messageSender) {
        Thread t = new Thread(() -> {
            messageSender.accept("jecharacters.chat.start");
            try (FileOutputStream fos = new FileOutputStream("logs/jecharacters.txt")) {
                OutputStreamWriter osw = new OutputStreamWriter(fos);
                osw.write(Profiler.runAsJson());
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
