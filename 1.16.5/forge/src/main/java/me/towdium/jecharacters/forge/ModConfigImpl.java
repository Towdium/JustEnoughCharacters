package me.towdium.jecharacters.forge;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.JechConfig.Spell;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.EnumValue;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import static net.minecraftforge.fml.config.ModConfig.Type.COMMON;

public class ModConfigImpl {

    private static final String PATH = "jecharacters.toml";
    private static ForgeConfigSpec common;

    static BooleanValue enableQuote;

    static EnumValue<Spell> enumKeyboard;
    static BooleanValue enableFZh2z;
    static BooleanValue enableFSh2s;
    static BooleanValue enableFCh2c;
    static BooleanValue enableFAng2an;
    static BooleanValue enableFIng2in;
    static BooleanValue enableFEng2en;
    static BooleanValue enableFU2v;

    static BooleanValue enableVerbose;
    static BooleanValue enableChat;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("General");
        b.comment("Keyboard for the checker to use");
        enumKeyboard = b.defineEnum("enumKeyboard", Spell.QUANPIN);
        b.comment("Set to true to enable fuzzy spelling zh <=> z");
        enableFZh2z = b.define("enableFZh2z", false);
        b.comment("Set to true to enable fuzzy spelling sh <=> s");
        enableFSh2s = b.define("enableFSh2s", false);
        b.comment("Set to true to enable fuzzy spelling ch <=> c");
        enableFCh2c = b.define("enableFCh2c", false);
        b.comment("Set to true to enable fuzzy spelling ang <=> an");
        enableFAng2an = b.define("enableFAng2an", false);
        b.comment("Set to true to enable fuzzy spelling ing <=> in");
        enableFIng2in = b.define("enableFIng2in", false);
        b.comment("Set to true to enable fuzzy spelling eng <=> en");
        enableFEng2en = b.define("enableFEng2en", false);
        b.comment("Set to true to enable fuzzy spelling u <=> v");
        enableFU2v = b.define("enableFU2v", false);
        b.comment("Set to false to disable chat message when entering world");
        enableChat = b.define("enableChat", true);
        b.comment("Set to true to disable JEI's split for search tokens");
        enableQuote = b.define("enableQuote", false);
        b.pop();

        b.push("Utilities");
        b.comment("Set true to print verbose debug message");
        enableVerbose = b.define("enableVerbose", false);
        b.pop();

        common = b.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(COMMON, ModConfigImpl.common, FMLPaths.CONFIGDIR.get().resolve(PATH).toString());
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
}
