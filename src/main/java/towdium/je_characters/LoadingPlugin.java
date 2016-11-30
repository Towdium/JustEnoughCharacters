package towdium.je_characters;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Map;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class LoadingPlugin implements IFMLLoadingPlugin {
    public static Logger log = LogManager.getLogger("je_characters");
    static boolean initialized = false;

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{"towdium.je_characters.ClassTransformer"};
    }

    @Override
    public String getModContainerClass() {
        return "towdium.je_characters.ModContainer";
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        JECConfig.preInit(((File) data.get("mcLocation")));
        initialized = true;
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
