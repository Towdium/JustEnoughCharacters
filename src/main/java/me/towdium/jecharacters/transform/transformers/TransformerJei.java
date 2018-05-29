package me.towdium.jecharacters.transform.transformers;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mcp.MethodsReturnNonnullByDefault;
import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.transform.Transformer;
import me.towdium.jecharacters.util.CachedFilter;
import mezz.jei.suffixtree.GeneralizedSuffixTree;
import org.objectweb.asm.tree.ClassNode;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Author: Towdium
 * Date:   13/06/17
 */

public class TransformerJei implements Transformer.Extended {
    @Override
    public boolean accepts(String name) {
        return JechConfig.EnumItems.EnableJEI.getProperty().getBoolean() && (
                name.equals("mezz.jei.ingredients.IngredientFilter")
        );
    }

    @Override
    public void transform(ClassNode n) {
        JechCore.LOG.info("Transforming class " + n.name + " for JEI integration.");
        Transformer.findMethod(n, "<init>").ifPresent(methodNode -> Transformer.transformConstruct(methodNode, "mezz/jei/suffixtree/GeneralizedSuffixTree",
                "me/towdium/jecharacters/transform/transformers/TransformerJei$FakeTreeB"));
        Transformer.findMethod(n, "createPrefixedSearchTree").ifPresent(methodNode ->
                Transformer.transformConstruct(methodNode, "mezz/jei/suffixtree/GeneralizedSuffixTree",
                        "me/towdium/jecharacters/transform/transformers/TransformerJei$FakeTreeB"));
    }

    @ParametersAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    public static class FakeTreeB extends GeneralizedSuffixTree {
        CachedFilter<Integer> cf;

        public FakeTreeB() {
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

        public int getHighestIndex() {
            return cf.computeCount();
        }
    }
}
