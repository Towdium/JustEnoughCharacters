package me.towdium.hecharacters.transform.transformers;

import me.towdium.hecharacters.HechConfig;
import me.towdium.hecharacters.match.PinyinMatcher;
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

    public static boolean contains(CharSequence a, CharSequence b, boolean c) {
        if (c) return PinyinMatcher.contains(a.toString().toLowerCase(), b.toString().toLowerCase());
        else return PinyinMatcher.contains(a.toString(), b);
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
                n, "kotlin/text/StringsKt", "contains", "me/towdium/hecharacters/transform/transformers/TransformerStrsKt", "contains",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z",
                false, Opcodes.INVOKESTATIC, null, null
        );
    }
}