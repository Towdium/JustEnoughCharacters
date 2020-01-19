package me.towdium.jecharacters.utils;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mcp.MethodsReturnNonnullByDefault;
import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.JustEnoughCharacters;
import me.towdium.pinin.PinIn;
import me.towdium.pinin.TreeSearcher;
import mezz.jei.suffixtree.GeneralizedSuffixTree;
import net.minecraft.client.util.SuffixArray;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.regex.Pattern;

import static me.towdium.pinin.Searcher.Logic.CONTAIN;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class Match {
    public static final PinIn context = new PinIn();
    static final Pattern p = Pattern.compile("a");

    public static String wrap(String s) {
        return JechConfig.enableQuote.get() ? '"' + s + '"' : s;
    }

    public static boolean contains(String s, CharSequence cs) {
        boolean b = context.contains(s, cs);
        if (JechConfig.enableVerbose.get())
            JustEnoughCharacters.logger.debug("contains(" + s + ',' + cs +")->" + b);
        return b;
    }

    public static boolean contains(CharSequence a, CharSequence b, boolean c) {
        if (c) return contains(a.toString().toLowerCase(), b.toString().toLowerCase());
        else return contains(a, b);
    }

    public static boolean equals(String s, Object o) {
        boolean b = o instanceof String && context.matches(s, (String) o);
        if (JechConfig.enableVerbose.get())
            JustEnoughCharacters.logger.debug("contains(" + s + ',' + o +")->" + b);
        return b;
    }

    public static boolean contains(CharSequence a, CharSequence b) {
        return contains(a.toString(), b);
    }

    public static java.util.regex.Matcher matcher(Pattern test, CharSequence name) {
        return matches(name.toString(), test.toString()) ? p.matcher("a") : p.matcher("");
    }

    public static boolean matches(String s1, String s2) {
        boolean start = s2.startsWith(".*");
        boolean end = s2.endsWith(".*");
        if (start && end && s2.length() < 4) end = false;
        if (start || end) s2 = s2.substring(start ? 2 : 0, s2.length() - (end ? 2 : 0));
        return contains(s1, s2);
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

        @Override
        public void add(T v, String k) {
            if (JechConfig.enableVerbose.get())
                JustEnoughCharacters.logger.debug("FakeArray:put(" + v + ',' + k + ')');
            tree.put(k, v);
        }

        @Override
        public void generate() {
        }

        @Override
        public List<T> search(String k) {
            if (JechConfig.enableVerbose.get())
                JustEnoughCharacters.logger.debug("FakeArray:search(" + k + ')');
            return tree.search(k);
        }
    }
}
