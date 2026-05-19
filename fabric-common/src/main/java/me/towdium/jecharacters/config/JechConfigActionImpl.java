package me.towdium.jecharacters.config;

import com.google.auto.service.AutoService;

@AutoService(JechConfigAction.class)
public class JechConfigActionImpl implements JechConfigAction {
    @Override
    public void setEnableVerbose(boolean enableVerbose) {
        JechConfigFabric.setEnableVerbose(enableVerbose);
    }

    @Override
    public int setKeyboard(JechConfig.Spell keyboard) {
        JechConfigFabric.setKeyboard(keyboard);
        return 0;
    }
}
