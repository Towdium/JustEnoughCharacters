package me.towdium.jecharacters.forge;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.config.JechConfigForge;
import me.towdium.jecharacters.utils.Match;

public class ModCommandImpl {
    public static int setKeyboard(JechConfig.Spell keyboard) {
        JechConfigForge.setKeyboard(keyboard);
        JechConfigForge.setEnableQuote(false);
        ModConfigImpl.save();
        ModConfigImpl.reload();
        Match.onConfigChange();
        return 0;
    }
}
