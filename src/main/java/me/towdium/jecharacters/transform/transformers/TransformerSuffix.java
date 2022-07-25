package me.towdium.jecharacters.transform.transformers;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.transform.Transformer;
import org.objectweb.asm.tree.MethodNode;

/**
 * Author: Towdium
 * Date: 18-9-17
 */
public class TransformerSuffix extends Transformer.Configurable {
    public static final String PATH = "me/towdium/jecharacters/util/Match$FakeArray";

    public TransformerSuffix() {
        reload();
    }

    @Override
    protected String[] getDefault() {
        return JechConfig.listDefaultSuffix;
    }

    @Override
    protected String[] getAdditional() {
        return JechConfig.listAdditionalSuffix;
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
