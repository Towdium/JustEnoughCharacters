package me.towdium.jecharacters.transform;

import me.towdium.jecharacters.transform.transformers.*;

import java.util.ArrayList;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
public class TransformerRegistry {
    public static TransformerStringUnique transformerString;
    public static TransformerRegExpUnique transformerRegExp;

    static ArrayList<Transformer> transformers = new ArrayList<>();

    static {
        transformerString = new TransformerStringUnique();
        transformerRegExp = new TransformerRegExpUnique();

        transformers.add(transformerRegExp);
        transformers.add(transformerString);
        transformers.add(new TransformerJei());
        transformers.add(new TransformerStringUniversal());
        transformers.add(new TransformerClassDump());
        transformers.add(new TransformerVanilla());
    }

    public static ArrayList<Transformer> getTransformer(String name) {
        ArrayList<Transformer> ret = new ArrayList<>();
        for (Transformer t : transformers) {
            if (t.accepts(name))
                ret.add(t);
        }
        return ret;
    }
}
