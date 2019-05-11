package me.towdium.jecharacters.match.matchables;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import me.towdium.jecharacters.match.Matchable;
import me.towdium.jecharacters.match.PinyinData;
import me.towdium.jecharacters.match.Utilities;
import me.towdium.jecharacters.match.Utilities.IndexSet;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;

/**
 * Author: Towdium
 * Date: 29/04/19
 */
public class Char implements Matchable {
    private static LoadingCache<Character, Char> cache = CacheBuilder.newBuilder().concurrencyLevel(1)
            .build(new CacheLoader<Character, Char>() {
                @Override
                @ParametersAreNonnullByDefault
                public Char load(Character ch) {
                    return new Char(ch);
                }
            });

    private Matchable[] patterns = new Matchable[0];

    private Char(char ch) {
        ArrayList<Matchable> list = new ArrayList<>();
        list.add(new Raw(ch));
        if (Utilities.isChinese(ch)) {
            String[] pinyin = PinyinData.get(ch);
            for (String s : pinyin) list.add(Pinyin.get(s));
        }
        patterns = list.toArray(patterns);
    }

    public static Char get(char ch) {
        return cache.getUnchecked(ch);
    }

    @Override
    public IndexSet match(String str, int start) {
        IndexSet ret = new IndexSet();
        for (Matchable p : patterns)
            ret.merge(p.match(str, start));
        return ret;
    }

    private static class Raw implements Matchable {
        private char ch;

        Raw(char ch) {
            this.ch = ch;
        }

        @Override
        public IndexSet match(String str, int start) {
            return str.charAt(start) == ch ? IndexSet.ONE : IndexSet.NONE;
        }
    }
}