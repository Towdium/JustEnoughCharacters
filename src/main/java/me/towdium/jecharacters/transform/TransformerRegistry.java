package me.towdium.jecharacters.transform;

import me.towdium.jecharacters.transform.transformers.*;

import java.util.ArrayList;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
public class TransformerRegistry {
    public static TransformerString transformerString;
    public static TransformerRegExp transformerRegExp;
    public static TransformerSuffix transformerSuffix;
    public static TransformerStrsKt transformerStrsKt;


    static ArrayList<Transformer> transformers = new ArrayList<>();

    static {
        transformerString = new TransformerString();
        transformerRegExp = new TransformerRegExp();
        transformerSuffix = new TransformerSuffix();
        transformerStrsKt = new TransformerStrsKt();
        transformers.add(transformerRegExp);
        transformers.add(transformerString);
        transformers.add(transformerSuffix);
        transformers.add(transformerStrsKt);
        transformers.add(new TransformerJei());
        //noinspection deprecation
        transformers.add(new TransformerRadical());
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
