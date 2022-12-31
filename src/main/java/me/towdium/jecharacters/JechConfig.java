package me.towdium.jecharacters;

import me.towdium.jecharacters.utils.Match;
import me.towdium.pinin.Keyboard;


public class JechConfig {

    public static final String GENERAL = "General";
    public static final String UTILITIES = "Utilities";

    public static boolean enableQuote;

    public static Spell enumKeyboard;
    public static boolean enableFZh2z;
    public static boolean enableFSh2s;
    public static boolean enableFCh2c;
    public static boolean enableFAng2an;
    public static boolean enableFIng2in;
    public static boolean enableFEng2en;
    public static boolean enableFU2v;

    //    public static ConfigValue<List<? extends String>> listDumpClass;
    public static boolean enableVerbose;
//    public static boolean enableChat;

    private static SimpleJsonConfig config;

    public static void register() {
        config = new SimpleJsonConfig();
        config.putValue(GENERAL, "EnumKeyboard", Spell.QUANPIN.name());
        config.putValue(GENERAL, "EnableFZh2z", false);
        config.putValue(GENERAL, "EnableFSh2s", false);
        config.putValue(GENERAL, "EnableFCh2c", false);
        config.putValue(GENERAL, "EnableFAng2an", false);
        config.putValue(GENERAL, "EnableFIng2in", false);
        config.putValue(GENERAL, "EnableFEng2en", false);
        config.putValue(GENERAL, "EnableFU2v", false);
        config.putValue(GENERAL, "EnableQuote", false);
        config.putValue(UTILITIES, "EnableVerbose", false);
    }

    public static void loadConfig() {
        config.load();
        enumKeyboard = config.getEnumValue(GENERAL, "EnumKeyboard", Spell.class);
        enableFZh2z = config.getBoolValue(GENERAL, "EnableFZh2z");
        enableFSh2s = config.getBoolValue(GENERAL, "EnableFSh2s");
        enableFCh2c = config.getBoolValue(GENERAL, "EnableFCh2c");
        enableFAng2an = config.getBoolValue(GENERAL, "EnableFAng2an");
        enableFIng2in = config.getBoolValue(GENERAL, "EnableFIng2in");
        enableFEng2en = config.getBoolValue(GENERAL, "EnableFEng2en");
        enableFU2v = config.getBoolValue(GENERAL, "EnableFU2v");
        enableQuote = config.getBoolValue(GENERAL, "EnableQuote");
        enableVerbose = config.getBoolValue(UTILITIES, "EnableVerbose");
        Match.onConfigChange();
        config.save();
    }


    public static void setKeyboard(Spell enumKeyboard) {
        JechConfig.enumKeyboard = enumKeyboard;
        config.setValue(GENERAL, "EnumKeyboard", enumKeyboard.name());
        config.save();
    }

    public static void setEnableQuote(boolean enableQuote) {
        JechConfig.enableQuote = enableQuote;
        config.setValue(GENERAL, "EnableQuote", enableQuote);
        config.save();
    }

    public static void setEnableVerbose(boolean enableVerbose) {
        JechConfig.enableVerbose = enableVerbose;
        config.setValue(UTILITIES, "EnableVerbose", enableVerbose);
        config.save();
    }

    public enum Spell {
        QUANPIN(Keyboard.QUANPIN), DAQIAN(Keyboard.DAQIAN),
        XIAOHE(Keyboard.XIAOHE), ZIRANMA(Keyboard.ZIRANMA);

        public final Keyboard keyboard;

        Spell(Keyboard keyboard) {
            this.keyboard = keyboard;
        }

        Keyboard get() {
            return keyboard;
        }
    }
}