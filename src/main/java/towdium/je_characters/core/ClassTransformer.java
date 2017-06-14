package towdium.je_characters.core;

import net.minecraft.launchwrapper.IClassTransformer;
import towdium.je_characters.transform.Transformer;
import towdium.je_characters.transform.TransformerRegistry;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class ClassTransformer implements IClassTransformer {

    @SuppressWarnings("SameParameterValue")
    @Override
    public byte[] transform(String s, String s1, byte[] bytes) {
        if (JechCore.initialized) {
            for (Transformer t : TransformerRegistry.getTransformer(s1)) {
                bytes = t.transform(bytes);
            }
            return bytes;
        } else {
            return bytes;
        }
    }
}
