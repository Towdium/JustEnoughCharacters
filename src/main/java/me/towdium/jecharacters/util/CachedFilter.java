package me.towdium.jecharacters.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
public class CachedFilter<T> {
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
                            baseItemSet.stream().filter(entry -> StringMatcher.checkStr(entry.key, filterText)).
                                    forEachOrdered(builder::add);
                            return builder.build();
                        }
                    });

    public ArrayList<T> search(String word) {
        ImmutableList<Entry> list = filteredItemMapsCache.getUnchecked(word);
        ArrayList<T> ret = new ArrayList<>(1000);
        list.forEach((entry -> ret.add(entry.value)));
        return ret;
    }

    public void put(String key, T value) throws IllegalStateException {
        fullList.add(new Entry(value, key));
    }

    public int computeCount() {
        return fullList.size();
    }

    private class Entry {
        T value;
        String key;

        Entry(T value, String key) {
            this.value = value;
            this.key = key;
        }
    }
}
