package me.towdium.jecharacters.transform.transformers;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.transform.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

import java.util.regex.Pattern;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
public class TransformerRegExp extends Transformer.Configurable {
    static final Pattern p = Pattern.compile("a");

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
                "me/towdium/jecharacters/util/Match", "matcher",
                "(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;",
                false, Opcodes.INVOKESTATIC, null, null
        );
        Transformer.transformInvoke(
                n, "java/lang/String", "matches",
                "me/towdium/jecharacters/util/Match", "matches",
                "(Ljava/lang/String;Ljava/lang/CharSequence;)Z",
                false, Opcodes.INVOKESTATIC, "(Ljava/lang/Object;)Z", "(Ljava/lang/String;)Z"
        );
    }

}