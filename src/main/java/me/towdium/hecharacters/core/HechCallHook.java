package me.towdium.hecharacters.core;

import me.towdium.hecharacters.match.PinyinMatcher;
import me.towdium.hecharacters.transform.TransformerRegistry;
import net.minecraftforge.fml.relauncher.IFMLCallHook;

import java.util.Map;

public class HechCallHook implements IFMLCallHook {
    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public Void call() {
        PinyinMatcher.contains("Test 这是一条测试文本", "Test zheshiytcswb");
        TransformerRegistry.getTransformer("some.class");
        HechCore.INITIALIZED = true;
        return null;
    }
}
