package me.towdium.jecharacters;

import net.minecraft.client.Minecraft;
import net.minecraft.client.searchtree.SuffixArray;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JustEnoughCharacters {

    public static final String MODID = "jecharacters";
    public static final Logger logger = LogManager.getLogger(MODID);
    public static String suffixClassName = SuffixArray.class.getCanonicalName();
    public static boolean messageSent = false;

    public static void printMessage(Component message) {
        Minecraft.getInstance().schedule(() -> Minecraft.getInstance().gui.getChat().addClientSystemMessage(message));
    }

}
