package me.towdium.jecharacters;

import mcp.MethodsReturnNonnullByDefault;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.util.Match;
import me.towdium.pinin.searchers.TreeSearcher;
import mezz.jei.api.IAdvancedSearchRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.search.ISearchIndex;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Set;

@JEIPlugin
public class JechJeiPlugin implements IModPlugin {
    @Override
    public void registerAdvancedSearch(IAdvancedSearchRegistry registry) {
        if (JechConfig.enableJEI) {
            JechCore.LOG.info("Registering JEI pinyin search index");
            registry.replaceIndex(JechSearchIndex::new);
        }
    }

    @ParametersAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    public static class JechSearchIndex<T> implements ISearchIndex<T> {
        private final TreeSearcher<T> tree = Match.searcher();

        @Override
        public void getSearchResults(String token, Set<T> results) {
            if (JechConfig.enableVerbose)
                JechCore.LOG.info("JechSearchIndex:search(" + token + ')');
            results.addAll(tree.search(token));
        }

        @Override
        public void getAllElements(Set<T> results) {
            results.addAll(tree.search(""));
        }

        @Override
        public void put(String key, T value) {
            if (JechConfig.enableVerbose)
                JechCore.LOG.info("JechSearchIndex:put(" + key + ',' + value + ')');
            tree.put(key, value);
        }

        @Override
        public String statistics() {
            return "JechSearchIndex:" + getClass().getSimpleName();
        }
    }
}
