package towdium.je_characters.transform.transformers;

import org.objectweb.asm.tree.ClassNode;
import towdium.je_characters.JechConfig;
import towdium.je_characters.core.JechCore;
import towdium.je_characters.transform.Transformer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
public class TransformerClassDump implements Transformer.Extended {
    Set<String> names = new HashSet<>();

    public TransformerClassDump() {
        Collections.addAll(names, JechConfig.EnumItems.ListDumpClass.getProperty().getStringList());
    }

    @Override
    public boolean accepts(String name) {
        return names.contains(name);
    }

    @Override
    public void transform(ClassNode n) {
        JechCore.LOG.info("Dumping methods in class " + n.name);
        n.methods.forEach(methodNode -> JechCore.LOG.info(methodNode.name));
    }
}
