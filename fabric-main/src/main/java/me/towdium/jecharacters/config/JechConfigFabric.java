package me.towdium.jecharacters.config;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.utils.Match;
import net.fabricmc.loader.api.FabricLoader;

import static me.towdium.jecharacters.JechConfig.*;

public class JechConfigFabric  {

    public static final SimpleJsonConfig config = new SimpleJsonConfig(FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE).toFile());

    static {
        config.sync(JechConfigFabric::loadConfig);
    }

    public static void register() {
        config.putValue(GENERAL, "EnumKeyboard", JechConfig.Spell.QUANPIN.name());
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

    public static void loadConfig(SimpleJsonConfig config) {
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

    public static void setKeyboard(JechConfig.Spell enumKeyboard) {
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

}
