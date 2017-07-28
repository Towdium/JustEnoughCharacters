package towdium.je_characters.jei;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.collect.ImmutableList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import towdium.je_characters.util.StringMatcher;

import java.util.ArrayList;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
public class CachedFilter {
    static ArrayList<CachedFilter> objs = new ArrayList<>();
    ArrayList<Entry> fullList = new ArrayList<>();
    private final LoadingCache<String, ImmutableList<Entry>> filteredItemMapsCache =
            CacheBuilder.newBuilder().maximumWeight(16).concurrencyLevel(1).
                    weigher((Weigher<String, ImmutableList<Entry>>) (key, value) -> 1).
                    build(new CacheLoader<String, ImmutableList<Entry>>() {
                        @Override
                        public ImmutableList<Entry> load(String filterText) throws Exception {
                            if (filterText.length() == 0) {
                                ImmutableList.Builder<Entry> builder = ImmutableList.builder();
                                builder.addAll(fullList);
                                return builder.build();
                            }
                            String prevFilterText = filterText.substring(0, filterText.length() - 1);
                            ImmutableList<Entry> baseItemSet = filteredItemMapsCache.get(prevFilterText);
                            ImmutableList.Builder<Entry> builder = ImmutableList.builder();
                            baseItemSet.stream().filter(entry -> StringMatcher.checkStr(entry.str, filterText)).
                                    forEachOrdered(builder::add);
                            return builder.build();
                        }
                    });

    public CachedFilter() {
        objs.add(this);
    }

    static void cache() {
    }

    public TIntSet search(String word) {
        ImmutableList<Entry> list = filteredItemMapsCache.getUnchecked(word);
        TIntSet ret = new TIntHashSet(1000);
        list.forEach((entry -> ret.add(entry.index)));
        return ret;
    }

    public void put(String key, int index) throws IllegalStateException {
        fullList.add(new Entry(index, key));
    }

    public int computeCount() {
        return fullList.size();
    }

    private class Entry {
        int index;
        String str;

        Entry(int index, String str) {
            this.index = index;
            this.str = str;
        }
    }
}
