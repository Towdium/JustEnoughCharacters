package me.towdium.hecharacters.transform.transformers;

import me.towdium.hecharacters.HechConfig;
import me.towdium.hecharacters.core.HechCore;
import me.towdium.hecharacters.transform.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Author: Towdium
 * Date:   13/06/17
 */

public class TransformerHei extends Transformer.Default {

    @Override
    public boolean accepts(String name) {
        return HechConfig.enableHEI && ("mezz.jei.search.PrefixInfo".equals(name) || "mezz.jei.search.SearchToken".equals(name));
    }

    @Override
    public void transform(ClassNode n) {
        HechCore.LOG.info("Transforming class " + n.name + " for HEI integration.");
        Transformer.findMethod(n, "<clinit>").ifPresent(methodNode ->
                Transformer.transformInvokeLambda(methodNode,
                        "mezz/jei/search/GeneralizedSuffixTree",
                        "<init>",
                        "()V",
                        "me/towdium/hecharacters/util/Match$FakeTree",
                        "<init>",
                        "()V"
                ));
        if (HechConfig.enableForceQuote) Transformer.findMethod(n, "parseSearchToken").ifPresent(methodNode -> {
            InsnList list = methodNode.instructions;

            for (int i = 0; i < list.size(); i++) {
                AbstractInsnNode node = list.get(i);
                if (node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                    MethodInsnNode methodInsn = (MethodInsnNode) node;
                    if ("java/util/regex/Pattern".equals(methodInsn.owner) && "matcher".equals(methodInsn.name) && "(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;".equals(methodInsn.desc)) {
                        list.insert(node.getPrevious(), new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "me/towdium/hecharacters/utils/Match", "wrap",
                                "(Ljava/lang/String;)Ljava/lang/String;", false));
                    }
                }
            }
        });
    }

}
