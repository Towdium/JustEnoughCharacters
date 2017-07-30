package towdium.je_characters.transform;

import towdium.je_characters.transform.transformers.*;

import java.util.ArrayList;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
public class TransformerRegistry {
    static final ArrayList<Transformer> NONE = new ArrayList<>();

    static ArrayList<Transformer> transformers = new ArrayList<>();

    static {
        transformers.add(new TransformerJei());
        transformers.add(new TransformerStringUnique());
        transformers.add(new TransformerRegExpUnique());
        transformers.add(new TransformerStringUniversal());
        transformers.add(new TransformerClassDump());
        transformers.add(new TransformerVanilla());
    }

    public static ArrayList<Transformer> getTransformer(String name) {
        ArrayList<Transformer> ret = NONE;
        for (Transformer t : transformers) {
            if (t.accepts(name)) {
                if (ret == NONE)
                    ret = new ArrayList<>();

                ret.add(t);
            }
        }
        return ret;
    }
}
