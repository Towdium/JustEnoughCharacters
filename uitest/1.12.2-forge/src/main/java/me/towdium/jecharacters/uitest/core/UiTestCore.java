package me.towdium.jecharacters.uitest.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Map;

@IFMLLoadingPlugin.Name("jechtest")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions({"me.towdium.jecharacters.uitest."})
public class UiTestCore implements IFMLLoadingPlugin {

    public static final Logger LOG = LogManager.getLogger("jechtest");

    public static File gameDir;

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{"me.towdium.jecharacters.uitest.core.UiTestTransformer"};
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        Object location = data.get("mcLocation");
        if (location instanceof File) gameDir = (File) location;
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
