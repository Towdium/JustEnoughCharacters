package me.towdium.jecharacters.transform.transformers;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.transform.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
public class TransformerRegExp extends Transformer.Configurable {
    public TransformerRegExp() {
        reload();
    }

    @Override
    protected String[] getDefault() {
        return JechConfig.listDefaultRegExp;
    }

    @Override
    protected String[] getAdditional() {
        return JechConfig.listAdditionalRegExp;
    }

    @Override
    protected String getName() {
        return "regular expression";
    }

    @Override
    protected void transform(MethodNode n) {
        Transformer.transformInvoke(
                n, "java/util/regex/Pattern", "matcher",
                "me/towdium/jecharacters/match/PinyinMatcher", "matcher",
                "(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;",
                false, Opcodes.INVOKESTATIC, null, null
        );
    }
}
