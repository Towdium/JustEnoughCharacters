package towdium.je_characters.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import towdium.je_characters.JechConfig;
import towdium.je_characters.transform.TransformerRegistry;
import towdium.je_characters.util.StringMatcher;

import java.io.File;
import java.util.Map;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class JechCore implements IFMLLoadingPlugin {
    public static final Logger LOG = LogManager.getLogger("je_characters");
    public static final String VERSION = "@VERSION@";
    public static File source;
    public static boolean INITIALIZED = false;
    public static boolean DEBUG = false;


    static {
        StringMatcher.checkStr("这是一条测试文本", "zheshiytcswb");
        //noinspection ResultOfMethodCallIgnored
        TransformerRegistry.class.toString();
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{"towdium.je_characters.core.ClassTransformer"};
    }

    @Override
    public String getModContainerClass() {
        return "towdium.je_characters.core.ModContainer";
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
