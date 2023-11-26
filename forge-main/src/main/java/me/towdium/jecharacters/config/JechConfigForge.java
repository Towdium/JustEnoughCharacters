package me.towdium.jecharacters.config;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.utils.Match;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import static net.minecraftforge.fml.config.ModConfig.Type.COMMON;

public class JechConfigForge {

    private static final String PATH = "jecharacters.toml";
    public final static ForgeConfigSpec common;

    static ForgeConfigSpec.BooleanValue enableQuote;

    static ForgeConfigSpec.EnumValue<JechConfig.Spell> enumKeyboard;
    static ForgeConfigSpec.BooleanValue enableFZh2z;
    static ForgeConfigSpec.BooleanValue enableFSh2s;
    static ForgeConfigSpec.BooleanValue enableFCh2c;
    static ForgeConfigSpec.BooleanValue enableFAng2an;
    static ForgeConfigSpec.BooleanValue enableFIng2in;
    static ForgeConfigSpec.BooleanValue enableFEng2en;
    static ForgeConfigSpec.BooleanValue enableFU2v;

    static ForgeConfigSpec.BooleanValue enableVerbose;
    static ForgeConfigSpec.BooleanValue enableChat;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("General");
        b.comment("Keyboard for the checker to use");
        enumKeyboard = b.defineEnum("enumKeyboard", JechConfig.Spell.QUANPIN);
        b.comment("Set to true to enable fuzzy spelling zh <=> z");
        enableFZh2z = b.define("enableFZh2z", JechConfig.enableFZh2z);
        b.comment("Set to true to enable fuzzy spelling sh <=> s");
        enableFSh2s = b.define("enableFSh2s", JechConfig.enableFSh2s);
        b.comment("Set to true to enable fuzzy spelling ch <=> c");
        enableFCh2c = b.define("enableFCh2c", JechConfig.enableFCh2c);
        b.comment("Set to true to enable fuzzy spelling ang <=> an");
        enableFAng2an = b.define("enableFAng2an", JechConfig.enableFAng2an);
        b.comment("Set to true to enable fuzzy spelling ing <=> in");
        enableFIng2in = b.define("enableFIng2in", JechConfig.enableFIng2in);
        b.comment("Set to true to enable fuzzy spelling eng <=> en");
        enableFEng2en = b.define("enableFEng2en", JechConfig.enableFEng2en);
        b.comment("Set to true to enable fuzzy spelling u <=> v");
        enableFU2v = b.define("enableFU2v", JechConfig.enableFU2v);
        b.comment("Set to false to disable chat message when entering world");
        enableChat = b.define("enableChat", JechConfig.enableChat);
        b.comment("Set to true to disable JEI's split for search tokens");
        enableQuote = b.define("enableQuote", JechConfig.enableQuote);
        b.pop();

        b.push("Utilities");
        b.comment("Set true to print verbose debug message");
        enableVerbose = b.define("enableVerbose", JechConfig.enableVerbose);
        b.pop();

        common = b.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(COMMON, JechConfigForge.common, FMLPaths.CONFIGDIR.get().resolve(PATH).toString());
    }

    public static void reload() {
        JechConfig.enumKeyboard = enumKeyboard.get();
        JechConfig.enableFZh2z = enableFZh2z.get();
        JechConfig.enableFSh2s = enableFSh2s.get();
        JechConfig.enableFCh2c = enableFCh2c.get();
        JechConfig.enableFAng2an = enableFAng2an.get();
        JechConfig.enableFIng2in = enableFIng2in.get();
        JechConfig.enableFEng2en = enableFEng2en.get();
        JechConfig.enableFU2v = enableFU2v.get();
        JechConfig.enableChat = enableChat.get();
        JechConfig.enableQuote = enableQuote.get();
        JechConfig.enableVerbose = enableVerbose.get();
        Match.onConfigChange();
    }

    public static void save() {
        enumKeyboard.set(JechConfig.enumKeyboard);
        enableFZh2z.set(JechConfig.enableFZh2z);
        enableFSh2s.set(JechConfig.enableFSh2s);
        enableFCh2c.set(JechConfig.enableFCh2c);
        enableFAng2an.set(JechConfig.enableFAng2an);
        enableFIng2in.set(JechConfig.enableFIng2in);
        enableFEng2en.set(JechConfig.enableFEng2en);
        enableFU2v.set(JechConfig.enableFU2v);
        enableChat.set(JechConfig.enableChat);
        enableQuote.set(JechConfig.enableQuote);
        enableVerbose.set(JechConfig.enableVerbose);
        common.save();
    }

    public static void setKeyboard(JechConfig.Spell keyboard) {
        JechConfig.enumKeyboard = keyboard;
        enumKeyboard.set(keyboard);
    }

    public static void setEnableQuote(boolean enableQuote) {
        JechConfig.enableQuote = enableQuote;
        JechConfigForge.enableQuote.set(enableQuote);
    }

}
