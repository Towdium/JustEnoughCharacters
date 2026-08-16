package me.towdium.jecharacters;

import me.towdium.jecharacters.config.JechConfig;
import me.towdium.jecharacters.config.JechConfig.Spell;
import me.towdium.jecharacters.utils.Match;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.EnumValue;

import java.util.function.Predicate;

import static net.neoforged.fml.config.ModConfig.Type.CLIENT;

public class JechConfigForge {
    public static ModConfigSpec common;

    public static BooleanValue enableQuote;

    public static EnumValue<Spell> enumKeyboard;
    public static BooleanValue enableFZh2z;
    public static BooleanValue enableFSh2s;
    public static BooleanValue enableFCh2c;
    public static BooleanValue enableFAng2an;
    public static BooleanValue enableFIng2in;
    public static BooleanValue enableFEng2en;
    public static BooleanValue enableFU2v;

    public static BooleanValue enableVerbose;
    public static BooleanValue enableChat;

    static {
        Predicate<Object> p = i -> i instanceof String;
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("General");
        b.comment("Keyboard for the checker to use");
        enumKeyboard = b.defineEnum("enumKeyboard", Spell.QUANPIN);
        b.comment("Set to true to enable fuzzy spelling zh <=> z");
        enableFZh2z = b.define("enableFZh2z", true);
        b.comment("Set to true to enable fuzzy spelling sh <=> s");
        enableFSh2s = b.define("enableFSh2s", true);
        b.comment("Set to true to enable fuzzy spelling ch <=> c");
        enableFCh2c = b.define("enableFCh2c", true);
        b.comment("Set to true to enable fuzzy spelling ang <=> an");
        enableFAng2an = b.define("enableFAng2an", true);
        b.comment("Set to true to enable fuzzy spelling ing <=> in");
        enableFIng2in = b.define("enableFIng2in", true);
        b.comment("Set to true to enable fuzzy spelling eng <=> en");
        enableFEng2en = b.define("enableFEng2en", true);
        b.comment("Set to true to enable fuzzy spelling u <=> v");
        enableFU2v = b.define("enableFU2v", true);
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

    static void register(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(CLIENT, JechConfigForge.common);
        modBus.addListener(ModConfigEvent.Loading.class, event -> JechConfigForge.refresh());
        modBus.addListener(ModConfigEvent.Reloading.class, event -> JechConfigForge.refresh());
    }

    public static void refresh() {
        JechConfig.enableQuote = enableQuote.get();
        JechConfig.enumKeyboard = enumKeyboard.get();
        JechConfig.enableFZh2z = enableFZh2z.get();
        JechConfig.enableFSh2s = enableFSh2s.get();
        JechConfig.enableFCh2c = enableFCh2c.get();
        JechConfig.enableFAng2an = enableFAng2an.get();
        JechConfig.enableFIng2in = enableFIng2in.get();
        JechConfig.enableFEng2en = enableFEng2en.get();
        JechConfig.enableFU2v = enableFU2v.get();
        JechConfig.enableVerbose = enableVerbose.get();
        JechConfig.enableChat = enableChat.get();
        Match.onConfigChange();
    }
}