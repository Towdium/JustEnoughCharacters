package me.towdium.jecharacters.asm.transformers;

import me.towdium.jecharacters.asm.ConfigurableTransformer;
import me.towdium.jecharacters.asm.ITransformer;
import me.towdium.jecharacters.asm.JechClassTransformer;
import org.objectweb.asm.tree.MethodNode;

public class SuffixArrayTransformer extends ConfigurableTransformer {
    @Override
    protected String getConfigOwner() {
        return "suffix";
    }

    @Override
    protected void transformMethod(MethodNode method) {
        ITransformer.transformSuffix(method, JechClassTransformer.suffixClassName);
    }
}
