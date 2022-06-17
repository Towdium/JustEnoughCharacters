package me.towdium.hecharacters.transform.transformers;

import me.towdium.hecharacters.HechConfig;
import me.towdium.hecharacters.transform.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
public class TransformerString extends Transformer.Configurable {
    public TransformerString() {
        reload();
    }

    @Override
    protected String[] getDefault() {
        return HechConfig.listDefaultString;
    }

    @Override
    protected String[] getAdditional() {
        return HechConfig.listAdditionalString;
    }

    @Override
    protected String getName() {
        return "string contains";
    }

    @Override
    protected void transform(MethodNode n) {
        Transformer.transformInvoke(
                n, "java/lang/String", "contains", "me/towdium/hecharacters/util/Match", "contains",
                "(Ljava/lang/String;Ljava/lang/CharSequence;)Z",
                false, Opcodes.INVOKESTATIC, "(Ljava/lang/Object;)Z", "(Ljava/lang/String;)Z"
        );
    }
}
