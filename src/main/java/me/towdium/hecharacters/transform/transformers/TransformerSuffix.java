package me.towdium.hecharacters.transform.transformers;

import me.towdium.hecharacters.HechConfig;
import me.towdium.hecharacters.transform.Transformer;
import org.objectweb.asm.tree.MethodNode;

/**
 * Author: Towdium
 * Date: 18-9-17
 */
public class TransformerSuffix extends Transformer.Configurable {
    public static final String PATH = "me/towdium/hecharacters/util/Match$FakeArray";

    public TransformerSuffix() {
        reload();
    }

    @Override
    protected String[] getDefault() {
        return HechConfig.listDefaultSuffix;
    }

    @Override
    protected String[] getAdditional() {
        return HechConfig.listAdditionalSuffix;
    }

    @Override
    protected String getName() {
        return "vanilla SuffixArray";
    }

    @Override
    protected void transform(MethodNode n) {
        Transformer.transformConstruct(n, "net/minecraft/client/util/SuffixArray", PATH);
        Transformer.transformConstruct(n, "cgx", PATH);
        Transformer.transformConstruct(n, "cgz", PATH);
    }

}
