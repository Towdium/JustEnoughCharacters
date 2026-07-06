package me.towdium.jecharacters.utils;

import me.towdium.jecharacters.JustEnoughCharacters;
import me.towdium.jecharacters.config.JechConfig;
import me.towdium.pinin.searchers.TreeSearcher;
import net.mezzdev.suffixtree.GeneralizedSuffixTree;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Consumer;

import static me.towdium.jecharacters.utils.Match.searcher;

public class FakeTree2<T> extends GeneralizedSuffixTree<T> {

    TreeSearcher<T> tree = searcher();

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
