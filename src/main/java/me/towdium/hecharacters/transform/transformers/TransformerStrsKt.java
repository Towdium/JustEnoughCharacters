package me.towdium.hecharacters.transform.transformers;

import me.towdium.hecharacters.HechConfig;
import me.towdium.hecharacters.transform.Transformer;
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
        return HechConfig.listDefaultStrsKt;
    }

    @Override
    protected String[] getAdditional() {
        return HechConfig.listAdditionalStrsKt;
    }

    @Override
    protected String getName() {
        return "Kotlin Strings contains";
    }

    @Override
    protected void transform(MethodNode n) {
        Transformer.transformInvoke(
                n, "kotlin/text/StringsKt", "contains",
                "me/towdium/hecharacters/util/Match", "contains",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z",
                false, Opcodes.INVOKESTATIC, null, null
        );
    }
}