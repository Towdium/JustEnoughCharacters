package me.towdium.jecharacters.core;

import me.towdium.jecharacters.JechConfig;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Map;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class JechCore implements IFMLLoadingPlugin {
    public static final Logger LOG = LogManager.getLogger("jecharacters");
    public static final String VERSION = "@VERSION@";
    public static File source;
    public static boolean INITIALIZED = false;

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{"me.towdium.jecharacters.core.JechClassTransformer"};
    }

    @Override
    public String getModContainerClass() {
        return "me.towdium.jecharacters.core.JechModContainer";
    }

    @Override
    public String getSetupClass() {
        return "me.towdium.jecharacters.core.JechCallHook";
    }

    @Override
    public void injectData(Map<String, Object> data) {
        JechConfig.preInit(((File) data.get("mcLocation")));
        source = (File) data.get("coremodLocation");
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
