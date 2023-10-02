package me.towdium.jecharacters.fabric;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.config.JechConfigFabric;
import me.towdium.jecharacters.config.SimpleJsonConfig;
import me.towdium.jecharacters.utils.Match;

import static me.towdium.jecharacters.JechConfig.GENERAL;
import static me.towdium.jecharacters.JechConfig.UTILITIES;

public class ModConfigImpl {


    static SimpleJsonConfig config;


    public static void register() {
        JechConfigFabric.register();
    }

    public static void loadConfig() {
        config.load();
        JechConfig.enumKeyboard = config.getEnumValue(GENERAL, "EnumKeyboard", JechConfig.Spell.class);
        JechConfig.enableFZh2z = config.getBoolValue(GENERAL, "EnableFZh2z");
        JechConfig.enableFSh2s = config.getBoolValue(GENERAL, "EnableFSh2s");
        JechConfig.enableFCh2c = config.getBoolValue(GENERAL, "EnableFCh2c");
        JechConfig.enableFAng2an = config.getBoolValue(GENERAL, "EnableFAng2an");
        JechConfig.enableFIng2in = config.getBoolValue(GENERAL, "EnableFIng2in");
        JechConfig.enableFEng2en = config.getBoolValue(GENERAL, "EnableFEng2en");
        JechConfig.enableFU2v = config.getBoolValue(GENERAL, "EnableFU2v");
        JechConfig.enableQuote = config.getBoolValue(GENERAL, "EnableQuote");
        JechConfig.enableVerbose = config.getBoolValue(UTILITIES, "EnableVerbose");
        Match.onConfigChange();
        config.save();
    }


    public static void reload() {
        loadConfig();
    }

    public static void save() {
        config.setValue(GENERAL, "EnumKeyboard", JechConfig.enumKeyboard.name());
        config.setValue(GENERAL, "EnableFZh2z", JechConfig.enableFZh2z);
        config.setValue(GENERAL, "EnableFSh2s", JechConfig.enableFSh2s);
        config.setValue(GENERAL, "EnableFCh2c", JechConfig.enableFCh2c);
        config.setValue(GENERAL, "EnableFAng2an", JechConfig.enableFAng2an);
        config.setValue(GENERAL, "EnableFIng2in", JechConfig.enableFIng2in);
        config.setValue(GENERAL, "EnableFEng2en", JechConfig.enableFEng2en);
        config.setValue(GENERAL, "EnableFU2v", JechConfig.enableFU2v);
        config.setValue(GENERAL, "EnableQuote", JechConfig.enableQuote);
        config.setValue(UTILITIES, "EnableVerbose", JechConfig.enableVerbose);
        config.save();
    }
}
