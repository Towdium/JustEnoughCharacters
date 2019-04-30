package me.towdium.jecharacters.transform;

import me.towdium.jecharacters.transform.transformers.*;

import java.util.ArrayList;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
public class TransformerRegistry {
    public static ArrayList<Transformer.Configurable> configurables = new ArrayList<>();
    public static ArrayList<Transformer> transformers = new ArrayList<>();

    static {
        configurables.add(new TransformerString());
        configurables.add(new TransformerRegExp());
        configurables.add(new TransformerSuffix());
        configurables.add(new TransformerStrsKt());
        transformers.addAll(configurables);
        transformers.add(new TransformerJei());
        transformers.add(new TransformerPsi());
        transformers.add(new TransformerClassDump());
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
