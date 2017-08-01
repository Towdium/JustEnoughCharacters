package me.towdium.jecharacters.core;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.transform.TransformerRegistry;
import me.towdium.jecharacters.util.StringMatcher;
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


    static {
        StringMatcher.checkStr("Test 这是一条测试文本", "Test zheshiytcswb");
        //noinspection ResultOfMethodCallIgnored
        TransformerRegistry.class.toString();
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{"me.towdium.jecharacters.core.ClassTransformer"};
    }

    @Override
    public String getModContainerClass() {
        return "me.towdium.jecharacters.core.ModContainer";
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        JechConfig.preInit(((File) data.get("mcLocation")));
        source = (File) data.get("coremodLocation");
        JechCore.INITIALIZED = true;
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
