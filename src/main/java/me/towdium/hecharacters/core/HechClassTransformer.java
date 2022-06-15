package me.towdium.hecharacters.core;

import me.towdium.hecharacters.transform.Transformer;
import me.towdium.hecharacters.transform.TransformerRegistry;
import net.minecraft.launchwrapper.IClassTransformer;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class HechClassTransformer implements IClassTransformer {

    @SuppressWarnings("SameParameterValue")
    @Override
    public byte[] transform(String s, String s1, byte[] bytes) {
        if (HechCore.INITIALIZED) {
            for (Transformer t : TransformerRegistry.getTransformer(s))
                bytes = t.transform(bytes);
            return bytes;
        } else {
            return bytes;
        }
    }
}
