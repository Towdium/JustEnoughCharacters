package me.towdium.jecharacters.utils;

import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Greetings {
    static final String[] MODS = {"jecharacters", "jecalculation"};
    static final Set<String> SENT = new HashSet<>();
    static final Map<String, String> FRIENDS = new HashMap<>() {{
        put("kiwi", "Snownee");
        put("i18nupdatemod", "TartaricAcid");
        put("touhou_little_maid", "TartaricAcid");
    }};

    public static void send(Logger logger, String self) {
        boolean master = true;
        for (String i : MODS) {
            if (FabricLoader.getInstance().isModLoaded(i)) {
                if (!i.equals(self)) master = false;
                break;
            }
        }

        if (master) {
            for (Map.Entry<String, String> i : FRIENDS.entrySet()) {
                if (FabricLoader.getInstance().isModLoaded(i.getKey()) && !SENT.contains(i.getValue())) {
                    logger.info("Good to see you, {}", i.getValue());
                    SENT.add(i.getValue());
                }
            }
        }
    }
}