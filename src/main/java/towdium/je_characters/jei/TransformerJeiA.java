package towdium.je_characters.jei;

import com.abahgat.suffixtree.GeneralizedSuffixTree;
import gnu.trove.set.TIntSet;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import towdium.je_characters.JECConfig;
import towdium.je_characters.transform.Transformer;

/**
 * Author: Towdium
 * Date:   13/06/17
 */
public class TransformerJeiA implements Transformer.Extended {
    public static void loadingHook() {
        CachedFilter.cache();
    }

    @Override
    public boolean accepts(String name) {
        return JECConfig.EnumItems.EnableJEI.getProperty().getBoolean() && (
                name.equals("mezz.jei.ItemFilterInternals")
                        || name.equals("mezz.jei.ingredients.IngredientFilterInternals")
        );
    }

    @Override
    public void transform(ClassNode n) {
        n.methods.stream().filter(methodNode -> methodNode.name.equals("<init>")).forEach(methodNode ->
                Transformer.transformConstruct(methodNode, "com/abahgat/suffixtree/GeneralizedSuffixTree",
                        "towdium/je_characters/jei/TransformerJeiA$FakeTree"));
        n.methods.stream().filter(methodNode -> methodNode.name.equals("buildSuffixTrees")).forEach(methodNode ->
                Transformer.transformHook(methodNode, "towdium/je_characters/jei/TransformerJeiA",
                        "loadingHook", "()V"));
    }

    public static class FakeTree extends GeneralizedSuffixTree {
        CachedFilter cf;

        public FakeTree() {
            cf = new CachedFilter();
        }

        @NotNull
        public TIntSet search(String word) {
            return cf.search(word);
        }

        public void put(String key, int index) throws IllegalStateException {
            cf.put(key, index);
        }

        public int computeCount() {
            return cf.computeCount();
        }
    }
}
