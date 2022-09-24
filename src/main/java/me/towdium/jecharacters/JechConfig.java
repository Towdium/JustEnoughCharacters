package me.towdium.jecharacters;

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

    /*
    static {



//        b.push("General");
//        b.comment("Keyboard for the checker to use");
//        enumKeyboard = b.defineEnum("enumKeyboard", Spell.QUANPIN);
//        b.comment("Set to true to enable fuzzy spelling zh <=> z");
//        enableFZh2z = b.define("enableFZh2z", false);
//        b.comment("Set to true to enable fuzzy spelling sh <=> s");
//        enableFSh2s = b.define("enableFSh2s", false);
//        b.comment("Set to true to enable fuzzy spelling ch <=> c");
//        enableFCh2c = b.define("enableFCh2c", false);
//        b.comment("Set to true to enable fuzzy spelling ang <=> an");
//        enableFAng2an = b.define("enableFAng2an", false);
//        b.comment("Set to true to enable fuzzy spelling ing <=> in");
//        enableFIng2in = b.define("enableFIng2in", false);
//        b.comment("Set to true to enable fuzzy spelling eng <=> en");
//        enableFEng2en = b.define("enableFEng2en", false);
//        b.comment("Set to true to enable fuzzy spelling u <=> v");
//        enableFU2v = b.define("enableFU2v", false);
//        b.comment("Set to false to disable chat message when entering world");
//        enableChat = b.define("enableChat", true);
//        b.comment("Set to true to disable JEI's split for search tokens");
//        enableQuote = b.define("enableQuote", false);
//        b.pop();

//        b.push("Utilities");
//        b.comment("List of classes to dump all the functions");
//        listDumpClass = b.defineList("listDumpClass", Collections.emptyList(), p);
//        b.comment("Set true to print verbose debug message");
//        enableVerbose = b.define("enableVerbose", false);
//        b.pop();
//
//        common = b.build();
    }
    
     */

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
        config.save();
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
        config.save();
    }


    public static void setKeyboard(Spell enumKeyboard) {
        JechConfig.enumKeyboard = enumKeyboard;
        config.setValue(GENERAL, "EnumKeyboard", enumKeyboard.name());
        config.save();
    }

    public static void setEnableQuote(boolean enableQuote) {
        JechConfig.enableQuote = enableQuote;
        config.setValue(GENERAL, "EnableQuote", enumKeyboard.name());
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