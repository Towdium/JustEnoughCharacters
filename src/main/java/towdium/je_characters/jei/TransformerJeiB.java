package towdium.je_characters.jei;

import gnu.trove.set.TIntSet;
import mezz.jei.suffixtree.GeneralizedSuffixTree;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import towdium.je_characters.transform.Transformer;

/**
 * Author: Towdium
 * Date:   13/06/17
 */
public class TransformerJeiB implements Transformer.Extended {


    public static void loadingHook() {
        CachedFilter.cache();
    }

    @Override
    public boolean accepts(String name) {
        return name.equals("mezz.jei.ingredients.IngredientFilter");
    }

    @Override
    public void transform(ClassNode n) {
        Transformer.findMethod(n, "<init>").ifPresent(methodNode -> {
            Transformer.transformConstruct(methodNode, "mezz/jei/suffixtree/GeneralizedSuffixTree",
                    "towdium/je_characters/jei/TransformerJeiB$FakeTree");
            Transformer.transformHook(methodNode, "towdium/je_characters/jei/TransformerJeiB",
                    "loadingHook", "()V");
        });
        Transformer.findMethod(n, "createPrefixedSearchTree").ifPresent(methodNode ->
                Transformer.transformConstruct(methodNode, "mezz/jei/suffixtree/GeneralizedSuffixTree",
                        "towdium/je_characters/jei/TransformerJeiB$FakeTree"));
    }

    public static class FakeTree extends GeneralizedSuffixTree {
        CachedFilter cf;

        public FakeTree() {
            cf = new CachedFilter();
        }

        @NotNull
        @Override
        public TIntSet search(String word) {
            return cf.search(word);
        }

        @Override
        public void put(String key, int index) throws IllegalStateException {
            cf.put(key, index);
        }

        @Override
        public int getHighestIndex() {
            return cf.computeCount();
        }
    }
}
