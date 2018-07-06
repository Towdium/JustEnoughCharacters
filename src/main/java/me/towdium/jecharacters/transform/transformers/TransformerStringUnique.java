package me.towdium.jecharacters.transform.transformers;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.transform.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
public class TransformerStringUnique implements Transformer.Extended {
    MethodDecoder md = new MethodDecoder();

    public TransformerStringUnique() {
        md.addAll(JechConfig.Item.LIST_DEFAULT_STRING_MATCH.getProperty().getStringList());
        md.addAll(JechConfig.Item.LIST_ADDITIONAL_STRING_MATCH.getProperty().getStringList());
        md.removeAll(JechConfig.Item.LIST_METHOD_BLACKLIST.getProperty().getStringList());
    }

    public void reload() {
        MethodDecoder mdt = new MethodDecoder();
        mdt.addAll(JechConfig.Item.LIST_DEFAULT_STRING_MATCH.getProperty().getStringList());
        mdt.addAll(JechConfig.Item.LIST_ADDITIONAL_STRING_MATCH.getProperty().getStringList());
        mdt.removeAll(JechConfig.Item.LIST_METHOD_BLACKLIST.getProperty().getStringList());
        md = mdt;
    }

    @Override
    public boolean accepts(String name) {
        return md.contains(name);
    }

    @Override
    public void transform(ClassNode n) {
        JechCore.LOG.info("Transforming class " + n.name + " for string contains.");
        Set<String> methods = md.getMethodsForClass(n.name.replace('/', '.'));
        if (!methods.isEmpty()) {
            List<MethodNode> s = n.methods.stream().filter(methodNode -> methods.contains(methodNode.name))
                    .collect(Collectors.toList());
            if (!s.isEmpty())
                s.forEach(methodNode ->
                        Transformer.transformInvoke(
                                methodNode, "java/lang/String", "contains", "me/towdium/jecharacters/util/StringMatcher",
                                "checkStr", "(Ljava/lang/String;Ljava/lang/CharSequence;)Z", false, Opcodes.INVOKESTATIC,
                                "(Ljava/lang/Object;)Z", "(Ljava/lang/String;)Z"
                        ));
            else
                JechCore.LOG.info("No function matched in class " + n.name);
        }
    }
}
