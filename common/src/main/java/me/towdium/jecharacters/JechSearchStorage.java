package me.towdium.jecharacters;

import me.towdium.jecharacters.config.JechConfig;
import me.towdium.pinin.searchers.TreeSearcher;
import mezz.jei.api.search.ISearchStorage;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.IntSummaryStatistics;
import java.util.function.Consumer;

import static me.towdium.jecharacters.utils.Match.searcher;

public class JechSearchStorage<T> implements ISearchStorage<T> {
    private final TreeSearcher<T> tree = searcher();
    private final IntSummaryStatistics keyLengthStats = new IntSummaryStatistics();

    @Override
    public void getSearchResults(@NotNull String token, @NotNull Consumer<Collection<T>> resultsConsumer) {
        if (JechConfig.enableVerbose) {
            JustEnoughCharacters.logger.info("JechSearchStorage:search(" + token + ')');
        }
        resultsConsumer.accept(tree.search(token));
    }

    @Override
    public void getAllElements(@NotNull Consumer<Collection<T>> resultsConsumer) {
        resultsConsumer.accept(tree.search(""));
    }

    @Override
    public void put(@NotNull String key, @NotNull T value) {
        if (JechConfig.enableVerbose) {
            JustEnoughCharacters.logger.info("JechSearchStorage:put(" + key + ',' + value + ')');
        }
        tree.put(key, value);

        int keyLength = key.length();
        keyLengthStats.accept(keyLength);
    }

    @Override
    public String statistics() {
        return "JechSearchStorage:" +
                "\nKey length stats: \n" + keyLengthStats;
    }
}
