package me.towdium.jecharacters;

import net.minecraft.client.searchtree.SuffixArray;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ServiceLoader;

public class JustEnoughCharacters {

    public static final String MODID = "jecharacters";
    public static final Logger logger = LogManager.getLogger(MODID);
    public static String suffixClassName = SuffixArray.class.getCanonicalName();
    public static boolean messageSent = false;
    private static final MessageSender messageSender = ServiceLoader.load(MessageSender.class, JustEnoughCharacters.class.getClassLoader())
                                                                    .findFirst()
                                                                    .orElseThrow();

    public static void printMessage(String translationKey) {
        messageSender.sendMessage(translationKey);
    }

}
