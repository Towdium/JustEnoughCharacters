package me.towdium.jecharacters;

import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.util.Match;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Set;

public class JechGuiFactory implements IModGuiFactory {
    @Override
    public void initialize(Minecraft minecraft) {
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        return new ConfigGUI(parentScreen);
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    public static class ConfigGUI extends GuiConfig {
        public ConfigGUI(GuiScreen parent) {
            super(parent, new ArrayList<IConfigElement>() {{
                add(new ConfigElement(JechConfig.Item.STRING_KEYBOARD.getProperty()));
                add(new ConfigElement(JechConfig.Item.ENABLE_FUZZY_ANG2AN.getProperty()));
                add(new ConfigElement(JechConfig.Item.ENABLE_FUZZY_ENG2EN.getProperty()));
                add(new ConfigElement(JechConfig.Item.ENABLE_FUZZY_ING2IN.getProperty()));
                add(new ConfigElement(JechConfig.Item.ENABLE_FUZZY_CH2C.getProperty()));
                add(new ConfigElement(JechConfig.Item.ENABLE_FUZZY_ZH2Z.getProperty()));
                add(new ConfigElement(JechConfig.Item.ENABLE_FUZZY_SH2S.getProperty()));
                add(new ConfigElement(JechConfig.Item.ENABLE_FUZZY_U2V.getProperty()));
                add(new ConfigElement(JechConfig.Item.ENABLE_CHAT_HELP.getProperty()));
            }}, JechCore.MODID, false, false, GuiConfig.getAbridgedConfigPath(JechConfig.config.toString()));
        }
    }

    @SuppressWarnings("unused")
    public static class ConfigHandler {
        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(JechCore.MODID)) {
                JechConfig.update();
            }
        }
    }
}