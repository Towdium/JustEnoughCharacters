package me.towdium.hecharacters.transform.transformers;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import mcp.MethodsReturnNonnullByDefault;
import me.towdium.hecharacters.HechConfig;
import me.towdium.hecharacters.match.PinyinTree;
import me.towdium.hecharacters.transform.Transformer;
import net.minecraft.client.util.SuffixArray;
import org.objectweb.asm.tree.MethodNode;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Author: Towdium
 * Date: 18-9-17
 */
public class TransformerSuffix extends Transformer.Configurable {
    public static final String PATH = "me/towdium/hecharacters/transform/" +
            "transformers/TransformerSuffix$FakeSuffixArray";

    public TransformerSuffix() {
        reload();
    }

    @Override
    protected String[] getDefault() {
        return HechConfig.listDefaultSuffix;
    }

    @Override
    protected String[] getAdditional() {
        return HechConfig.listAdditionalSuffix;
    }

    @Override
    protected String getName() {
        return "vanilla SuffixArray";
    }

    @Override
    protected void transform(MethodNode n) {
        Transformer.transformConstruct(n, "net/minecraft/client/util/SuffixArray", PATH);
        Transformer.transformConstruct(n, "cgx", PATH);
        Transformer.transformConstruct(n, "cgz", PATH);
    }

    @ParametersAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    public static class FakeSuffixArray<T> extends SuffixArray<T> {
        PinyinTree tree = new PinyinTree();
        Int2ObjectMap<T> map = new Int2ObjectOpenHashMap<>();
        int count = 0;

        @Override
        public void add(T v, String k) {
            tree.put(k, count);
            map.put(count, v);
            count++;
        }

        @Override
        public void generate() {
        }

        @Override
        public List<T> search(String k) {
            return tree.search(k).stream().sorted()
                    .map(map::get).collect(Collectors.toList());
        }
    }
}
