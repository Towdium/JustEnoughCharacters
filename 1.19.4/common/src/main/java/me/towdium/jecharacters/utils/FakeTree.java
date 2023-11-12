package me.towdium.jecharacters.utils;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.JustEnoughCharacters;
import me.towdium.pinin.searchers.TreeSearcher;
import mezz.jei.core.search.suffixtree.GeneralizedSuffixTree;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class FakeTree<T> extends GeneralizedSuffixTree<T> {

    TreeSearcher<T> tree = Match.searcher();

    @Override
    public void getSearchResults(@NotNull String word, @NotNull Consumer<Collection<T>> resultsConsumer) {
        if (JechConfig.enableVerbose) {
            JustEnoughCharacters.logger.info("FakeTree:search(" + word + ')');
        }
        resultsConsumer.accept(tree.search(word));
    }


    @Override
    public void put(@NotNull String key, @NotNull T value) {
        if (JechConfig.enableVerbose) {
            JustEnoughCharacters.logger.info("FakeTree:put(" + key + ',' + value + ')');
        }
        tree.put(key, value);
    }

    @Override
    public void getAllElements(@NotNull Consumer<Collection<T>> resultsConsumer) {
        resultsConsumer.accept(tree.search(""));
    }

}
