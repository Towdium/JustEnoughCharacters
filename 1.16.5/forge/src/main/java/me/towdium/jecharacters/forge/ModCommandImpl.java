package me.towdium.jecharacters.forge;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.utils.Match;

public class ModCommandImpl {
    public static int setKeyboard(JechConfig.Spell keyboard) {
        ModConfigImpl.enumKeyboard.set(keyboard);
        ModConfigImpl.enableQuote.set(false);
        ModConfigImpl.save();
        ModConfigImpl.reload();
        Match.onConfigChange();
        return 0;
    }
}
