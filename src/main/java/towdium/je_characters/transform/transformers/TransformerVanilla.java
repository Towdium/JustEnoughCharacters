package towdium.je_characters.transform.transformers;

import net.minecraft.client.util.SuffixArray;
import org.objectweb.asm.tree.ClassNode;
import towdium.je_characters.core.JechCore;
import towdium.je_characters.transform.Transformer;
import towdium.je_characters.util.CachedFilter;

import java.util.List;

/**
 * Author: towdium
 * Date:   17-7-30.
 */
public class TransformerVanilla implements Transformer.Extended {
    public boolean accepts(String name) {
        return name.equals("net.minecraft.client.util.SearchTree");
    }

    @Override
    public void transform(ClassNode n) {
        JechCore.LOG.info("Transforming vanilla SearchTree.");
        Transformer.findMethod(n, "<init>").ifPresent(m ->
                Transformer.transformConstruct(m, "net/minecraft/client/util/SuffixArray",
                        "towdium/je_characters/transform/transformers/TransformerVanilla$FakeSuffixArray"));
        Transformer.findMethod(n, "recalculate", "()V").ifPresent(m ->
                Transformer.transformConstruct(m, "net/minecraft/client/util/SuffixArray",
                        "towdium/je_characters/transform/transformers/TransformerVanilla$FakeSuffixArray"));
        Transformer.findMethod(n, "<init>").ifPresent(m ->
                Transformer.transformConstruct(m, "cgx",
                        "towdium/je_characters/transform/transformers/TransformerVanilla$FakeSuffixArray"));
        Transformer.findMethod(n, "a", "()V").ifPresent(m ->
                Transformer.transformConstruct(m, "cgx",
                        "towdium/je_characters/transform/transformers/TransformerVanilla$FakeSuffixArray"));
    }

    public static class FakeSuffixArray<T> extends SuffixArray<T> {
        CachedFilter<T> filter;

        public FakeSuffixArray() {
            filter = new CachedFilter<>();
        }

        @Override
        public void add(T p_194057_1_, String p_194057_2_) {
            filter.put(p_194057_2_, p_194057_1_);
        }

        @Override
        public void generate() {
        }

        @Override
        public List<T> search(String p_194055_1_) {
            return filter.search(p_194055_1_);
        }
    }
}
