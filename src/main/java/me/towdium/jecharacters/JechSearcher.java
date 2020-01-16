package me.towdium.jecharacters;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mcp.MethodsReturnNonnullByDefault;
import me.towdium.pinin.TreeSearcher;
import mezz.jei.suffixtree.GeneralizedSuffixTree;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class JechSearcher extends GeneralizedSuffixTree {
    TreeSearcher<Integer> tree = new TreeSearcher<>(me.towdium.pinin.Searcher.Logic.CONTAIN, JustEnoughCharacters.context);
    int highestIndex = -1;

    public IntSet search(String word) {
        return new IntOpenHashSet(tree.search(word));
    }

    public void put(String key, int index) throws IllegalStateException {
        if (index < highestIndex) {
            String err = "The input index must not be less than any of the previously " +
                    "inserted ones. Got " + index + ", expected at least " + highestIndex;
            throw new IllegalStateException(err);
        } else highestIndex = index;
        tree.put(key, index);
    }

    @Override
    public int getHighestIndex() {
        return highestIndex;
    }

    public static String wrap(String s) {
        return JechConfig.enableQuote.get() ? '"' + s + '"' : s;
    }
}
