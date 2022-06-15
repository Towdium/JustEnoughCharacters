package me.towdium.hecharacters.core;

import me.towdium.hecharacters.HechConfig;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Map;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class HechCore implements IFMLLoadingPlugin {
    public static final String MODID = "hecharacters";
    public static final Logger LOG = LogManager.getLogger(MODID);
    public static final String VERSION = "@VERSION@";
    public static File source;
    public static boolean INITIALIZED = false;

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{"me.towdium.hecharacters.core.HechClassTransformer"};
    }

    @Override
    public String getModContainerClass() {
        return "me.towdium.hecharacters.core.HechModContainer";
    }

    @Override
    public String getSetupClass() {
        return "me.towdium.hecharacters.core.HechCallHook";
    }

    @Override
    public void injectData(Map<String, Object> data) {
        HechConfig.init(((File) data.get("mcLocation")));
        source = (File) data.get("coremodLocation");
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }


}
