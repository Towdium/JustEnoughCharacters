package me.towdium.jecharacters.transform.transformers;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.transform.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
@Deprecated
@SuppressWarnings("DeprecatedIsStillUsed")
public class TransformerRadical implements Transformer.Extended {
    @Override
    public boolean accepts(String name) {
        return JechConfig.Item.ENABLE_RADICAL_MODE.getProperty().getBoolean();
    }

    @Override
    public void transform(ClassNode n) {
        n.methods.forEach(methodNode -> {
            if (Transformer.transformInvoke(
                    methodNode, "java/lang/String", "contains", "me/towdium/jecharacters/util/StringMatcher", "checkStr",
                    "(Ljava/lang/String;Ljava/lang/CharSequence;)Z", false, Opcodes.INVOKESTATIC,
                    "(Ljava/lang/Object;)Z", "(Ljava/lang/String;)Z"
            )) {
                JechCore.LOG.info("Transformed method " + n.name + ":" + methodNode.name + " in radical mode.");
            }
        });
    }
}
