package me.towdium.hecharacters.transform.transformers;

import me.towdium.hecharacters.HechConfig;
import me.towdium.hecharacters.core.HechCore;
import me.towdium.hecharacters.transform.Transformer;
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
        Collections.addAll(names, HechConfig.Item.LIST_DUMP_CLASS_FUNC.getProperty().getStringList());
    }

    @Override
    public boolean accepts(String name) {
        if (HechConfig.enableDumpClassName) HechCore.LOG.info("Class name: " + name);
        return names.contains(name);
    }

    @Override
    public void transform(ClassNode n) {
        HechCore.LOG.info("Dumping methods in class " + n.name);
        n.methods.forEach(methodNode -> HechCore.LOG.info(methodNode.name));
    }
}
