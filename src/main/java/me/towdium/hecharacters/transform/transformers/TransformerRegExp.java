package me.towdium.hecharacters.transform.transformers;

import me.towdium.hecharacters.HechConfig;
import me.towdium.hecharacters.transform.Transformer;
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
        return HechConfig.listDefaultRegExp;
    }

    @Override
    protected String[] getAdditional() {
        return HechConfig.listAdditionalRegExp;
    }

    @Override
    protected String getName() {
        return "regular expression";
    }

    @Override
    protected void transform(MethodNode n) {
        Transformer.transformInvoke(
                n, "java/util/regex/Pattern", "matcher",
                "me/towdium/hecharacters/util/Match", "matcher",
                "(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;",
                false, Opcodes.INVOKESTATIC, null, null
        );
        Transformer.transformInvoke(
                n, "java/lang/String", "matches",
                "me/towdium/hecharacters/util/Match", "matches",
                "(Ljava/lang/String;Ljava/lang/String;)Z",
                false, Opcodes.INVOKESTATIC, "(Ljava/lang/Object;)Z", "(Ljava/lang/String;)Z"
        );
    }

}
