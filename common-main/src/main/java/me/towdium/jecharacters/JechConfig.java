package me.towdium.jecharacters;

import me.towdium.pinin.Keyboard;

public class JechConfig {


    public static final String CONFIG_FILE = "jecharacters.json";
    public static final String GENERAL = "General";
    public static final String UTILITIES = "Utilities";

    public static boolean enableQuote = false;

    public static Spell enumKeyboard = Spell.QUANPIN;
    public static boolean enableFZh2z = false;
    public static boolean enableFSh2s = false;
    public static boolean enableFCh2c = false;
    public static boolean enableFAng2an = false;
    public static boolean enableFIng2in = false;
    public static boolean enableFEng2en = false;
    public static boolean enableFU2v = false;

    public static boolean enableVerbose;
    public static boolean enableChat;


    public enum Spell {
        QUANPIN(Keyboard.QUANPIN), DAQIAN(Keyboard.DAQIAN),
        XIAOHE(Keyboard.XIAOHE), ZIRANMA(Keyboard.ZIRANMA),
        SOUGOU(Keyboard.SOUGOU), GUOBIAO(Keyboard.GUOBIAO),
        MICROSOFT(Keyboard.MICROSOFT), PINYINPP(Keyboard.PINYINPP),
        ZIGUANG(Keyboard.ZIGUANG);


        public final Keyboard keyboard;

        Spell(Keyboard keyboard) {
            this.keyboard = keyboard;
        }

        public Keyboard get() {
            return keyboard;
        }
    }
}