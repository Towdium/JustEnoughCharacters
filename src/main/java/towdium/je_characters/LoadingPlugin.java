package towdium.je_characters;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class LoadingPlugin implements IFMLLoadingPlugin {
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

    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
