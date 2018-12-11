package me.towdium.jecharacters.transform.transformers;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.transform.Transformer;
import me.towdium.jecharacters.util.StringMatcher;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Author: Towdium
 * Date: 18-12-12
 */
public class TransformerStrsKt implements Transformer.Extended {
    MethodDecoder md;

    public TransformerStrsKt() {
        reload();
    }

    public static boolean contains(CharSequence a, CharSequence b, boolean c) {
        if (c) return StringMatcher.checkStr(a.toString().toLowerCase(), b.toString().toLowerCase());
        else return StringMatcher.checkStr(a.toString(), b);
    }

    public void reload() {
        MethodDecoder mdt = new MethodDecoder();
        mdt.addAll(JechConfig.listDefaultStrsKt);
        mdt.addAll(JechConfig.listAdditionalStrsKt);
        mdt.removeAll(JechConfig.listMethodBlacklist);
        md = mdt;
    }

    @Override
    public boolean accepts(String name) {
        return md.contains(name);
    }

    @Override
    public void transform(ClassNode n) {
        JechCore.LOG.info("Transforming class " + n.name + " for Kotlin Strings contains.");
        Set<String> methods = md.getMethodsForClass(n.name.replace('/', '.'));
        if (!methods.isEmpty()) {
            List<MethodNode> s = n.methods.stream().filter(methodNode -> methods.contains(methodNode.name))
                    .collect(Collectors.toList());
            if (!s.isEmpty())
                s.forEach(methodNode ->
                        Transformer.transformInvoke(
                                methodNode, "kotlin/text/StringsKt", "contains",
                                "me/towdium/jecharacters/transform/transformers/TransformerStrsKt",
                                "contains", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z",
                                false, Opcodes.INVOKESTATIC, null, null
                        ));
            else JechCore.LOG.info("No function matched in class " + n.name);
        }
    }
}