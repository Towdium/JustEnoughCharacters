package me.towdium.jecharacters;

import me.towdium.pinin.Keyboard;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.EnumValue;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static net.minecraftforge.fml.config.ModConfig.Type.COMMON;

public class JechConfig {
    public static final String PATH = "jecharacters.toml";
    public static ForgeConfigSpec common;

    public static BooleanValue enableQuote;

    public static EnumValue<Spell> enumKeyboard;
    public static BooleanValue enableFZh2z;
    public static BooleanValue enableFSh2s;
    public static BooleanValue enableFCh2c;
    public static BooleanValue enableFAng2an;
    public static BooleanValue enableFIng2in;
    public static BooleanValue enableFEng2en;
    public static BooleanValue enableFU2v;

    public static ConfigValue<List<? extends String>> listDumpClass;
    public static BooleanValue enableVerbose;
    public static BooleanValue enableChat;

    static {
        Predicate<Object> p = i -> i instanceof String;
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
        b.comment("List of classes to dump all the functions");
        listDumpClass = b.defineList("listDumpClass", Collections.emptyList(), p);
        b.comment("Set true to print verbose debug message");
        enableVerbose = b.define("enableVerbose", false);
        b.pop();

        common = b.build();
    }

    static void register() {
        ModLoadingContext.get().registerConfig(COMMON, JechConfig.common,
                FMLPaths.CONFIGDIR.get().resolve(PATH).toString());
    }

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

        Keyboard get() {
            return keyboard;
        }
    }
}