package me.towdium.jecharacters.transform.transformers;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.transform.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.Set;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
public class TransformerRegExp implements Transformer.Extended {
    MethodDecoder md;

    public TransformerRegExp() {
        reload();
    }

    public void reload() {
        MethodDecoder mdt = new MethodDecoder();
        mdt.addAll(JechConfig.listDefaultRegExp);
        mdt.addAll(JechConfig.listAdditionalRegExp);
        mdt.removeAll(JechConfig.listMethodBlacklist);
        md = mdt;
    }

    @Override
    public boolean accepts(String name) {
        return md.contains(name);
    }

    @Override
    public void transform(ClassNode n) {
        JechCore.LOG.info("Transforming class " + n.name + " for regular expression.");
        Set<String> methods = md.getMethodsForClass(n.name.replace('/', '.'));
        if (!methods.isEmpty())
            n.methods.stream().filter(methodNode -> methods.contains(methodNode.name))
                    .forEach(methodNode -> Transformer.transformInvoke(
                            methodNode, "java/util/regex/Pattern", "matcher", "me/towdium/jecharacters/util/StringMatcher",
                            "checkReg", "(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;",
                            false, Opcodes.INVOKESTATIC, null, null
                    ));
        else JechCore.LOG.info("No function matched in class " + n.name);
    }
}
