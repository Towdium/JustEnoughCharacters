package me.towdium.jecharacters;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mcp.MethodsReturnNonnullByDefault;
import me.towdium.pinin.PinIn;
import me.towdium.pinin.TreeSearcher;
import mezz.jei.suffixtree.GeneralizedSuffixTree;
import net.minecraft.client.util.SuffixArray;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

import static me.towdium.pinin.Searcher.Logic.CONTAIN;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class JechMatcher {
    public static final PinIn context = new PinIn();

    public static String wrap(String s) {
        return JechConfig.enableQuote.get() ? '"' + s + '"' : s;
    }

    public static boolean contains(String s, CharSequence cs) {
        return true;
    }

    public static class FakeTree extends GeneralizedSuffixTree {
        TreeSearcher<Integer> tree = new TreeSearcher<>(CONTAIN, context);
        int highestIndex = -1;

        public IntSet search(String word) {
            return new IntOpenHashSet(tree.search(word));
        }

        @Override
        public void put(String key, int index) throws IllegalStateException {
            if (JechConfig.enableVerbose.get())
                JustEnoughCharacters.logger.debug("FakeTree:put(" + key + ',' + index + ')');
            if (index < highestIndex) {
                String err = "The input index must not be less than any of the previously " +
                        "inserted ones. Got " + index + ", expected at least " + highestIndex;
                throw new IllegalStateException(err);
            } else highestIndex = index;
            tree.put(key, index);
        }

        @Override
        public int getHighestIndex() {
            if (JechConfig.enableVerbose.get())
                JustEnoughCharacters.logger.debug("FakeTree:getHighestIndex()->" + highestIndex);
            return highestIndex;
        }
    }

    public static class FakeArray<T> extends SuffixArray<T> {
        TreeSearcher<T> tree = new TreeSearcher<>(CONTAIN, context);

        public FakeArray() {
            System.out.println("!");
        }

        @Override
        public void add(T v, String k) {
            tree.put(k, v);
        }

        @Override
        public void generate() {
        }

        @Override
        public List<T> search(String k) {
            return tree.search(k);
        }
    }
}
