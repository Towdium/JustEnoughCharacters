package me.towdium.jecharacters.asm.transformers;

import me.towdium.jecharacters.asm.ConfigurableTransformer;
import me.towdium.jecharacters.asm.ITransformer;
import org.objectweb.asm.tree.MethodNode;

public class ContainsTransformer extends ConfigurableTransformer {
    @Override
    protected String getConfigOwner() {
        return "contains";
    }

    @Override
    protected void transformMethod(MethodNode method) {
        ITransformer.transformContains(method);
    }
}
