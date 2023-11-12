package me.towdium.jecharacters;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.architectury.injectables.annotations.ExpectPlatform;
import me.towdium.jecharacters.asm.JechClassTransformer;
import me.towdium.jecharacters.utils.Greetings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;

public class JustEnoughCharacters {

    public static final String MODID = "jecharacters";
    public static final Logger logger = LogManager.getLogger("JustEnoughCharacters");

    public static void init() {
        Greetings.send(logger, MODID, PlatformUtils::isModLoaded);
        ModConfig.register();
        ModConfig.reload();
        JechClassTransformer.suffixClassName = getSuffixClassName();
    }

    public static <S> void registerCommand(CommandDispatcher<S> dispatcher, Function<String, LiteralArgumentBuilder<S>> literal) {
        JechCommand.register(literal, dispatcher, PlatformUtils::sendMessage, ModCommand::setKeyboard, ModConfig::save);
    }

    @ExpectPlatform
    private static String getSuffixClassName() {
        return "net/minecraft/client/searchtree/SuffixArray";
    }


}
