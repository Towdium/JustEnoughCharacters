package me.towdium.jecharacters.transform.transformers;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.transform.Transformer;
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
        return JechConfig.listDefaultString;
    }

    @Override
    protected String[] getAdditional() {
        return JechConfig.listDefaultString;
    }

    @Override
    protected String getName() {
        return "string contains";
    }

    @Override
    protected void transform(MethodNode n) {
        Transformer.transformInvoke(
                n, "java/lang/String", "contains",
                "me/towdium/jecharacters/match/PinyinMatcher", "contains",
                "(Ljava/lang/String;Ljava/lang/CharSequence;)Z",
                false, Opcodes.INVOKESTATIC, "(Ljava/lang/Object;)Z", "(Ljava/lang/String;)Z"
        );
    }
}
