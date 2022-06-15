package me.towdium.hecharacters.transform.transformers;

import it.unimi.dsi.fastutil.ints.IntSet;
import mcp.MethodsReturnNonnullByDefault;
import me.towdium.hecharacters.HechConfig;
import me.towdium.hecharacters.core.HechCore;
import me.towdium.hecharacters.match.PinyinTree;
import me.towdium.hecharacters.transform.Transformer;
import mezz.jei.suffixtree.GeneralizedSuffixTree;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Author: Towdium
 * Date:   13/06/17
 */

public class TransformerJei extends Transformer.Default {
    @SuppressWarnings("unused")
    public static String wrap(String s) {
        return "\"" + s + "\"";
    }

    @Override
    public boolean accepts(String name) {
        return HechConfig.enableHEI && name.equals("mezz.jei.ingredients.IngredientFilter");
    }

    @Override
    public void transform(ClassNode n) {
        HechCore.LOG.info("Transforming class " + n.name + " for HEI integration.");
        Transformer.findMethod(n, "<init>").ifPresent(methodNode ->
                Transformer.transformConstruct(methodNode, "mezz/jei/suffixtree/GeneralizedSuffixTree", "me/towdium/hecharacters/transform/transformers/TransformerJei$FakeTree"));
        Transformer.findMethod(n, "createPrefixedSearchTree").ifPresent(methodNode ->
                Transformer.transformConstruct(methodNode, "mezz/jei/suffixtree/GeneralizedSuffixTree", "me/towdium/hecharacters/transform/transformers/TransformerJei$FakeTree"));
        if (HechConfig.enableForceQuote) Transformer.findMethod(n, "getElements").ifPresent(methodNode -> {
            InsnList list = methodNode.instructions;
            list.insert(list.get(3), new MethodInsnNode(Opcodes.INVOKESTATIC, "me/towdium/hecharacters/transform/transformers/TransformerJei", "wrap",
                    "(Ljava/lang/String;)Ljava/lang/String;", false));
        });
    }

    @ParametersAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    public static class FakeTree extends GeneralizedSuffixTree {
        PinyinTree graph = new PinyinTree();
        int highestIndex = -1;

        public IntSet search(String word) {
            return graph.search(word);
        }

        public void put(String key, int index) throws IllegalStateException {
            if (index < highestIndex) {
                String err = "The input index must not be less than any of the previously " +
                        "inserted ones. Got " + index + ", expected at least " + highestIndex;
                throw new IllegalStateException(err);
            } else highestIndex = index;
            graph.put(key, index);
        }

        @Override
        public int getHighestIndex() {
            return highestIndex;
        }
    }
}
