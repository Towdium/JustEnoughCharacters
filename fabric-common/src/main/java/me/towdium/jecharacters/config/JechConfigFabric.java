package me.towdium.jecharacters.config;

import me.towdium.jecharacters.utils.Match;

import static me.towdium.jecharacters.config.JechConfig.GENERAL;
import static me.towdium.jecharacters.config.JechConfig.UTILITIES;

public class JechConfigFabric {

    private static SimpleJsonConfig config;

    public static SimpleJsonConfig getConfig() {
        return config;
    }

    public static void register() {
        config = new SimpleJsonConfig();
        config.putValue(GENERAL, "EnumKeyboard", JechConfig.Spell.QUANPIN.name());
        config.putValue(GENERAL, "EnableFZh2z", true);
        config.putValue(GENERAL, "EnableFSh2s", true);
        config.putValue(GENERAL, "EnableFCh2c", true);
        config.putValue(GENERAL, "EnableFAng2an", true);
        config.putValue(GENERAL, "EnableFIng2in", true);
        config.putValue(GENERAL, "EnableFEng2en", true);
        config.putValue(GENERAL, "EnableFU2v", true);
        config.putValue(GENERAL, "EnableQuote", false);
        config.putValue(UTILITIES, "EnableVerbose", false);
    }

    public static void refresh() {
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
    }

    public static void loadConfig() {
        config.load();
        refresh();
        config.save();
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

    public static void setKeyboard(JechConfig.Spell enumKeyboard) {
        JechConfig.enumKeyboard = enumKeyboard;
        config.setValue(GENERAL, "EnumKeyboard", enumKeyboard.name());
        config.save();
        refresh();
    }

    public static void setEnableQuote(boolean enableQuote) {
        JechConfig.enableQuote = enableQuote;
        config.setValue(GENERAL, "EnableQuote", enableQuote);
        config.save();
        refresh();
    }

    public static void setEnableVerbose(boolean enableVerbose) {
        JechConfig.enableVerbose = enableVerbose;
        config.setValue(UTILITIES, "EnableVerbose", enableVerbose);
        config.save();
        refresh();
    }

}
