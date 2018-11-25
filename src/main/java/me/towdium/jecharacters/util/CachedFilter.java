package me.towdium.jecharacters.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.collect.ImmutableList;
import mcp.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Author: Towdium
 * Date:   12/06/17
 */

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CachedFilter<T> {
    static Set<CachedFilter> instances = Collections.newSetFromMap(
            new WeakHashMap<>());

    ArrayList<Entry> fullList = new ArrayList<>();
    private final LoadingCache<String, ImmutableList<Entry>> cache =
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
                            ImmutableList<Entry> baseItemSet = cache.get(prevFilterText);
                            ImmutableList.Builder<Entry> builder = ImmutableList.builder();
                            baseItemSet.stream().parallel().filter(entry ->
                                    StringMatcher.checkStr(entry.key, filterText)).forEachOrdered(builder::add);
                            return builder.build();
                        }
                    });

    public CachedFilter() {
        instances.add(this);
    }

    public static void invalidate() {
        instances.forEach(i -> i.cache.invalidateAll());
    }

    public void put(String key, T value) throws IllegalStateException {
        fullList.add(new Entry(value, key.toLowerCase()));
    }

    public ArrayList<T> search(String word) {
        ImmutableList<Entry> list = cache.getUnchecked(word.toLowerCase());
        ArrayList<T> ret = new ArrayList<>(1000);
        list.forEach((entry -> ret.add(entry.value)));
        return ret;
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
