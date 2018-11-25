package me.towdium.jecharacters.transform.transformers;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mcp.MethodsReturnNonnullByDefault;
import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.transform.Transformer;
import me.towdium.jecharacters.util.CachedFilter;
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

public class TransformerJei implements Transformer.Extended {
    @SuppressWarnings("unused")
    public static String wrap(String s) {
        return "\"" + s + "\"";
    }

    @Override
    public boolean accepts(String name) {
        return JechConfig.enableJEI && name.equals("mezz.jei.ingredients.IngredientFilter");
    }

    @Override
    public void transform(ClassNode n) {
        JechCore.LOG.info("Transforming class " + n.name + " for JEI integration.");
        Transformer.findMethod(n, "<init>").ifPresent(methodNode -> Transformer.transformConstruct(methodNode, "mezz/jei/suffixtree/GeneralizedSuffixTree",
                "me/towdium/jecharacters/transform/transformers/TransformerJei$FakeTree"));
        Transformer.findMethod(n, "createPrefixedSearchTree").ifPresent(methodNode ->
                Transformer.transformConstruct(methodNode, "mezz/jei/suffixtree/GeneralizedSuffixTree",
                        "me/towdium/jecharacters/transform/transformers/TransformerJei$FakeTree"));
        if (JechConfig.enableForceQuote) Transformer.findMethod(n, "getElements").ifPresent(methodNode -> {
            InsnList list = methodNode.instructions;
            list.insert(list.get(3), new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "me/towdium/jecharacters/transform/transformers/TransformerJei", "wrap",
                    "(Ljava/lang/String;)Ljava/lang/String;", false));
        });
    }

    @ParametersAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    public static class FakeTree extends GeneralizedSuffixTree {
        CachedFilter<Integer> cf;

        public FakeTree() {
            cf = new CachedFilter<>();
        }

        public IntSet search(String word) {
            StackTraceElement[] t = Thread.currentThread().getStackTrace();
            String func = t[2].getMethodName();
            return func.equals("getSearchResults") || func.equals("search") ?
                    new IntOpenHashSet(cf.search(word)) : super.search(word);
        }

        public void put(String key, int index) throws IllegalStateException {
            super.put(key, index);
            cf.put(key, index);
        }
    }
}
