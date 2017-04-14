package towdium.je_characters.jei;

/*
 * Author: towdium
 * Date:   26/01/17
 */


import com.abahgat.suffixtree.GeneralizedSuffixTree;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.collect.ImmutableList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import towdium.je_characters.CheckHelper;

import java.util.ArrayList;
import java.util.function.Consumer;

import static towdium.je_characters.CheckHelper.foreachChar;


public class MyFilter extends GeneralizedSuffixTree {

    static ArrayList<MyFilter> filters = new ArrayList<>();

    private ArrayList<Entry> fullList = new ArrayList<>();

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
                            baseItemSet.stream().filter(entry -> CheckHelper.checkStr(entry.str, filterText)).
                                    forEachOrdered(builder::add);
                            return builder.build();
                        }
                    });

    public MyFilter() {
        if (filters.size() == 0) {
            onBuildStarted();
        }
        filters.add(this);
    }

    static void onBuildStarted() {
        CheckHelper.buildingMode(true);
    }

    static void onBuildFinished() {
        filters.forEach(MyFilter::cache);
        filters.forEach(myFilter -> myFilter.sendList(CheckHelper.addBase));
        filters.clear();
        CheckHelper.buildingMode(false);
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

    public void cache() {


        StringBuilder stringBuilder = new StringBuilder();
        foreachChar(c1 -> foreachChar(c2 ->
                search(stringBuilder.delete(0, 2).append(c1).append(c2).toString())));
    }

    public void sendList(Consumer<String> consumer) {
        fullList.forEach(entry -> consumer.accept(entry.str));
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
