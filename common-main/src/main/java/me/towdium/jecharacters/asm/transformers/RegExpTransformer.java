package me.towdium.jecharacters.asm.transformers;

import me.towdium.jecharacters.asm.ConfigurableTransformer;
import me.towdium.jecharacters.asm.ITransformer;
import org.objectweb.asm.tree.MethodNode;

public class RegExpTransformer extends ConfigurableTransformer {
    @Override
    protected String getConfigOwner() {
        return "regExp";
    }

    @Override
    protected void transformMethod(MethodNode method) {
        ITransformer.transformRegExp(method);
    }
}
