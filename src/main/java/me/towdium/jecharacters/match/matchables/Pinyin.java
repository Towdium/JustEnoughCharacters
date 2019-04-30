package me.towdium.jecharacters.match.matchables;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.match.Matchable;
import me.towdium.jecharacters.match.Utilities.IndexSet;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.HashSet;

import static me.towdium.jecharacters.match.Utilities.strCmp;

/**
 * Author: Towdium
 * Date: 21/04/19
 */
public class Pinyin implements Matchable {
    private static LoadingCache<String, Pinyin> cache = CacheBuilder.newBuilder().concurrencyLevel(1)
            .build(new CacheLoader<String, Pinyin>() {
                @Override
                @ParametersAreNonnullByDefault
                public Pinyin load(String str) {
                    return new Pinyin(str);
                }
            });

    private Phoneme initial;
    private Phoneme finale;
    private Phoneme tone;

    public Pinyin(String str) {
        set(str);
    }

    public static Pinyin get(String str) {
        return cache.getUnchecked(str);
    }

    public static void refresh() {
        Phoneme.refresh();
        cache.asMap().forEach((s, p) -> p.set(s));
    }

    private void set(String str) {
        String[] elements = JechConfig.keyboard.separate(str);
        initial = Phoneme.get(elements[0]);
        finale = Phoneme.get(elements[1]);
        tone = Phoneme.get(elements[2]);
    }

    public IndexSet match(String str, int start) {
        IndexSet ret = new IndexSet(0x1);
        ret = initial.match(str, ret, start);
        ret.merge(finale.match(str, ret, start));
        ret.merge(tone.match(str, ret, start));
        return ret;
    }

    @Override
    public String toString() {
        return "" + initial + finale + tone;
    }

    @SuppressWarnings("Duplicates")
    private static class Phoneme {
        private static LoadingCache<String, Phoneme> cache =
                CacheBuilder.newBuilder().concurrencyLevel(1)
                        .build(new CacheLoader<String, Phoneme>() {
                            @Override
                            @ParametersAreNonnullByDefault
                            public Phoneme load(String str) {
                                return new Phoneme(str);
                            }
                        });

        String[] strs;

        @Override
        public String toString() {
            return strs[0];
        }

        public Phoneme(String str) {
            HashSet<String> ret = new HashSet<>();
            ret.add(str);

            if (JechConfig.enableFuzzyCh2c && str.startsWith("c")) Collections.addAll(ret, "c", "ch");
            if (JechConfig.enableFuzzySh2s && str.startsWith("s")) Collections.addAll(ret, "s", "sh");
            if (JechConfig.enableFuzzyZh2z && str.startsWith("z")) Collections.addAll(ret, "z", "zh");
            if (JechConfig.enableFuzzyU2v && str.startsWith("v"))
                ret.add("u" + str.substring(1));
            if ((JechConfig.enableFuzzyAng2an && str.endsWith("ang"))
                    || (JechConfig.enableFuzzyEng2en && str.endsWith("eng"))
                    || (JechConfig.enableFuzzyIng2in && str.endsWith("ing")))
                ret.add(str.substring(0, str.length() - 1));
            if ((JechConfig.enableFuzzyAng2an && str.endsWith("an"))
                    || (str.endsWith("en") && JechConfig.enableFuzzyEng2en)
                    || (str.endsWith("in") && JechConfig.enableFuzzyIng2in))
                ret.add(str + 'g');
            strs = ret.stream().map(JechConfig.keyboard::keys).toArray(String[]::new);
        }

        public static Phoneme get(String str) {
            return cache.getUnchecked(str);
        }

        public static void refresh() {
            cache.invalidateAll();
        }

        IndexSet match(String source, IndexSet idx, int start) {
            if (strs.length == 1 && strs[0].isEmpty()) return new IndexSet(idx);
            else {
                IndexSet ret = new IndexSet();
                idx.foreach(i -> {
                    for (String str : strs) {
                        int size = strCmp(source, str, i + start);
                        if (i + start + size == source.length()) ret.set(i + size);  // ending match
                        else if (size == str.length()) ret.set(i + size); // full match
                    }
                    return true;
                });
                return ret;
            }
        }
    }
}
