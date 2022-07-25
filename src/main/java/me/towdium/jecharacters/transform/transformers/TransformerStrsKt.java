package me.towdium.jecharacters.transform.transformers;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.transform.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

/**
 * Author: Towdium
 * Date: 18-12-12
 */
public class TransformerStrsKt extends Transformer.Configurable {
    public TransformerStrsKt() {
        reload();
    }

    @Override
    protected String[] getDefault() {
        return JechConfig.listDefaultStrsKt;
    }

    @Override
    protected String[] getAdditional() {
        return JechConfig.listAdditionalStrsKt;
    }

    @Override
    protected String getName() {
        return "Kotlin Strings contains";
    }

    @Override
    protected void transform(MethodNode n) {
        Transformer.transformInvoke(
                n, "kotlin/text/StringsKt", "contains",
                "me/towdium/jecharacters/util/Match", "contains",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z",
                false, Opcodes.INVOKESTATIC, null, null
        );
    }
}