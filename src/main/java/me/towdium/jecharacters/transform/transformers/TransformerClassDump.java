package me.towdium.jecharacters.transform.transformers;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.transform.Transformer;
import org.objectweb.asm.tree.ClassNode;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
public class TransformerClassDump extends Transformer.Default {
    Set<String> names = new HashSet<>();

    public TransformerClassDump() {
        Collections.addAll(names, JechConfig.Item.LIST_DUMP_CLASS_FUNC.getProperty().getStringList());
    }

    @Override
    public boolean accepts(String name) {
        if (JechConfig.enableDumpClassName) JechCore.LOG.info("Class name: " + name);
        return names.contains(name);
    }

    @Override
    public void transform(ClassNode n) {
        JechCore.LOG.info("Dumping methods in class " + n.name);
        n.methods.forEach(methodNode -> JechCore.LOG.info(methodNode.name));
    }
}
