package me.towdium.jecharacters.utils;

import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class Greetings {
    static final String[] MODS = {"jecharacters", "jecalculation"};
    static final Set<String> SENT = new HashSet<>();
    static final Map<String, String> FRIENDS = new HashMap<>();

    static {
        FRIENDS.put("kiwi", "Snownee");
        FRIENDS.put("i18nupdatemod", "TartaricAcid");
        FRIENDS.put("touhou_little_maid", "TartaricAcid");
    }

    public static void send(Logger logger, String self, Predicate<String> loadedTest) {
        boolean master = true;
        for (String i : MODS) {
            if (loadedTest.test(i)) {
                if (!i.equals(self)) master = false;
                break;
            }
        }

        if (master) {
            for (Map.Entry<String, String> i : FRIENDS.entrySet()) {
                if (loadedTest.test(i.getKey()) && !SENT.contains(i.getValue())) {
                    logger.info("Good to see you, {}", i.getValue());
                    SENT.add(i.getValue());
                }
            }
        }
    }
}