package me.towdium.jecharacters.utils;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.JustEnoughCharacters;
import me.towdium.pinin.searchers.TreeSearcher;
import mezz.jei.search.suffixtree.GeneralizedSuffixTree;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@SuppressWarnings("unused")
public class FakeTree<T> extends GeneralizedSuffixTree<T> {

    TreeSearcher<T> tree = Match.searcher();

    @Override
    public void getSearchResults(@NotNull String word, @NotNull Set<T> results) {
        if (JechConfig.enableVerbose) {
            JustEnoughCharacters.logger.info("FakeTree:search(" + word + ')');
        }
        results.addAll(tree.search(word));
    }

    @Override
    public void put(@NotNull String key, @NotNull T value) {
        if (JechConfig.enableVerbose) {
            JustEnoughCharacters.logger.info("FakeTree:put(" + key + ',' + value + ')');
        }
        tree.put(key, value);
    }

    @Override
    public void getAllElements(Set<T> results) {
        results.addAll(tree.search(""));
    }

}
