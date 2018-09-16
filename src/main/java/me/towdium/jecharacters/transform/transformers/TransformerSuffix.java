package me.towdium.jecharacters.transform.transformers;

import mcp.MethodsReturnNonnullByDefault;
import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.transform.Transformer;
import me.towdium.jecharacters.util.CachedFilter;
import net.minecraft.client.util.SuffixArray;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Author: Towdium
 * Date: 18-9-17
 */
public class TransformerSuffix implements Transformer.Extended {
    MethodDecoder md;

    public TransformerSuffix() {
        reload();
    }

    public void reload() {
        MethodDecoder mdt = new MethodDecoder();
        mdt.addAll(JechConfig.listDefaultSuffix);
        mdt.addAll(JechConfig.listAdditionalSuffix);
        mdt.removeAll(JechConfig.listMethodBlacklist);
        md = mdt;
    }

    @Override
    public boolean accepts(String name) {
        return md.contains(name);
    }

    @Override
    public void transform(ClassNode n) {
        JechCore.LOG.info("Transforming class " + n.name + " for SuffixArray.");
        Set<String> methods = md.getMethodsForClass(n.name.replace('/', '.'));
        if (!methods.isEmpty()) {
            List<MethodNode> s = n.methods.stream().filter(methodNode -> methods.contains(methodNode.name))
                    .collect(Collectors.toList());
            if (!s.isEmpty())
                s.forEach(methodNode -> {
                    Transformer.transformConstruct(methodNode, "net/minecraft/client/util/SuffixArray",
                            "me/towdium/jecharacters/transform/transformers/TransformerSuffix$FakeSuffixArray");
                    Transformer.transformConstruct(methodNode, "cgx",
                            "me/towdium/jecharacters/transform/transformers/TransformerSuffix$FakeSuffixArray");
                    Transformer.transformConstruct(methodNode, "cgz",
                            "me/towdium/jecharacters/transform/transformers/TransformerSuffix$FakeSuffixArray");
                });
            else JechCore.LOG.info("No function matched in class " + n.name);
        }
    }

    @ParametersAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    public static class FakeSuffixArray<T> extends SuffixArray<T> {
        CachedFilter<T> filter;

        public FakeSuffixArray() {
            filter = new CachedFilter<>();
        }

        @Override
        public void add(T v, String k) {
            filter.put(k, v);
        }

        @Override
        public void generate() {
        }

        @Override
        public List<T> search(String k) {
            return filter.search(k);
        }
    }
}
