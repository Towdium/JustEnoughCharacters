package me.towdium.jecharacters.fabric;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.config.JechConfigFabric;

public class ModCommandImpl {
    public static int setKeyboard(JechConfig.Spell keyboard) {
        JechConfigFabric.setKeyboard(keyboard);
        JechConfig.tryReload();
        return 0;
    }
}
