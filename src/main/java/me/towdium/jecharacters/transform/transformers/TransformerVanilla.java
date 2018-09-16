package me.towdium.jecharacters.transform.transformers;

import mcp.MethodsReturnNonnullByDefault;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.transform.Transformer;
import me.towdium.jecharacters.util.CachedFilter;
import net.minecraft.client.util.SuffixArray;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Author: towdium
 * Date:   17-7-30.
 */
public class TransformerVanilla implements Transformer.Extended {
    public boolean accepts(String name) {
        return name.equals("net.minecraft.client.util.SearchTree") || name.equals("cgw");
    }

    @Override
    public void transform(ClassNode n) {
        JechCore.LOG.info("Transforming vanilla SearchTree.");
        Optional<MethodNode> n1 = Transformer.findMethod(n, "<init>");
        Optional<MethodNode> n2 = Transformer.findMethod(n, "recalculate", "()V").map(Optional::of)
                .orElseGet(() -> Transformer.findMethod(n, "a", "()V"));
        Consumer<MethodNode> c = m -> {
            Transformer.transformConstruct(m, "net/minecraft/client/util/SuffixArray",
                    "me/towdium/jecharacters/transform/transformers/TransformerVanilla$FakeSuffixArray");
            Transformer.transformConstruct(m, "cgx",
                    "me/towdium/jecharacters/transform/transformers/TransformerVanilla$FakeSuffixArray");
            Transformer.transformConstruct(m, "cgz",
                    "me/towdium/jecharacters/transform/transformers/TransformerVanilla$FakeSuffixArray");
        };
        n1.ifPresent(c);
        n2.ifPresent(c);
    }

    @ParametersAreNonnullByDefault
    @MethodsReturnNonnullByDefault
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
