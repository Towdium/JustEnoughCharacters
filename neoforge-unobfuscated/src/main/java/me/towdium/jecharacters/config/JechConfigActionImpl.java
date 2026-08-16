package me.towdium.jecharacters.config;

import com.google.auto.service.AutoService;
import me.towdium.jecharacters.JechConfigForge;

@AutoService(JechConfigAction.class)
public class JechConfigActionImpl implements JechConfigAction {
    @Override
    public void setEnableVerbose(boolean enableVerbose) {
        JechConfigForge.enableVerbose.set(enableVerbose);
        JechConfigForge.refresh();
    }

    @Override
    public int setKeyboard(JechConfig.Spell keyboard) {
        JechConfigForge.enumKeyboard.set(keyboard);
        JechConfigForge.refresh();
        return 0;
    }
}
