package towdium.je_characters.jei;

import com.google.common.base.Predicate;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.collect.ImmutableList;
import mezz.jei.IngredientBaseListFactory;
import mezz.jei.ItemFilter;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import net.minecraft.item.ItemStack;
import towdium.je_characters.CheckHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The code is originally developed by mezz and used in JEI earlier than 1.11.
 * In 1.11, JEI started to use suffix tree for item filtering, which is incompatible with Chinese pinyin system.
 * So I have to port the original work to 1.11 and replace the new behavior.
 * Most of the code remains unchanged and some methods is tweaked to adapt to the new interface.
 * I don't claim any credit of this work and the original licence is MIT.
 */

public class OldItemFilter extends ItemFilter {

    /**
     * A cache for fast searches while typing or using backspace. Maps filterText to filteredItemMaps
     */
    private final LoadingCache<String, ImmutableList<IIngredientListElement>> filteredItemMapsCache = CacheBuilder.newBuilder()
            .maximumWeight(16)
            .weigher(new OneWeigher())
            .concurrencyLevel(1)
            .build(new ItemFilterCacheLoader());

    private ImmutableList<IIngredientListElement> baseList;
    /**
     * {@link #getItemStacks()} is slow, so cache the previous value in case someone requests it often.
     */
    private ImmutableList<ItemStack> itemStacksCached = ImmutableList.of();
    @Nullable
    private String filterCached;

    public OldItemFilter() {
        this.baseList = IngredientBaseListFactory.create();
    }

    public void rebuild() {
        this.baseList = IngredientBaseListFactory.create();
        this.filteredItemMapsCache.invalidateAll();
    }

    public ImmutableList<Object> getIngredientList() {
        String[] filters = Config.getFilterText().split("\\|");

        if (filters.length == 1) {
            String filter = filters[0];
            ImmutableList.Builder<Object> b = ImmutableList.builder();
            filteredItemMapsCache.getUnchecked(filter).forEach(iIngredientListElement ->
                    b.add(iIngredientListElement.getIngredient()));
            return b.build();
        } else {
            ImmutableList.Builder<Object> ingredientList = ImmutableList.builder();
            for (String filter : filters) {
                List<IIngredientListElement> ingredients = filteredItemMapsCache.getUnchecked(filter);
                ingredients.forEach(iIngredientListElement -> ingredientList.add(iIngredientListElement.getIngredient()));
            }
            return ingredientList.build();
        }
    }

    public ImmutableList<ItemStack> getItemStacks() {
        if (!Config.getFilterText().equals(filterCached)) {
            ImmutableList.Builder<ItemStack> filteredStacks = ImmutableList.builder();
            for (Object element : getIngredientList()) {
                Object ingredient = ((IIngredientListElement) element).getIngredient();
                if (ingredient instanceof ItemStack) {
                    filteredStacks.add((ItemStack) ingredient);
                }
            }
            itemStacksCached = filteredStacks.build();
            filterCached = Config.getFilterText();
        }
        return itemStacksCached;
    }

    public int size() {
        return getIngredientList().size();
    }

    private static class OneWeigher implements Weigher<String, ImmutableList<IIngredientListElement>> {
        public int weigh(String key, ImmutableList<IIngredientListElement> value) {
            return 1;
        }
    }

    private static class FilterPredicate implements Predicate<IIngredientListElement> {
        private final List<String> searchTokens = new ArrayList<>();
        private final List<String> modNameTokens = new ArrayList<>();
        private final List<String> tooltipTokens = new ArrayList<>();
        private final List<String> oreDictTokens = new ArrayList<>();
        private final List<String> creativeTabTokens = new ArrayList<>();
        private final List<String> colorTokens = new ArrayList<>();

        public FilterPredicate(String filterText) {
            String[] tokens = filterText.split(" ");
            for (String token : tokens) {
                if (token.isEmpty()) {
                    continue;
                }

                if (token.startsWith("@")) {
                    addTokenWithoutPrefix(token, modNameTokens);
                } else if (token.startsWith("#")) {
                    addTokenWithoutPrefix(token, tooltipTokens);
                } else if (token.startsWith("$")) {
                    addTokenWithoutPrefix(token, oreDictTokens);
                } else if (token.startsWith("%")) {
                    addTokenWithoutPrefix(token, creativeTabTokens);
                } else if (token.startsWith("^")) {
                    addTokenWithoutPrefix(token, colorTokens);
                } else {
                    searchTokens.add(token);
                }
            }
        }

        private static void addTokenWithoutPrefix(String token, List<String> tokensList) {
            if (token.length() < 2) {
                return;
            }
            String tokenText = token.substring(1);
            tokensList.add(tokenText);
        }

        private static boolean stringContainsTokens(String comparisonString, List<String> tokens) {
            for (String token : tokens) {
                if (!CheckHelper.checkStr(comparisonString, token)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean apply(@Nullable IIngredientListElement input) {
            return input != null &&
                    stringContainsTokens(input.getModName(), modNameTokens) &&
                    stringContainsTokens(input.getTooltipString(), tooltipTokens) &&
                    stringContainsTokens(input.getOreDictString(), oreDictTokens) &&
                    stringContainsTokens(input.getCreativeTabsString(), creativeTabTokens) &&
                    stringContainsTokens(input.getColorString(), colorTokens) &&
                    stringContainsTokens(input.getDisplayName(), searchTokens);
        }
    }

    private class ItemFilterCacheLoader extends CacheLoader<String, ImmutableList<IIngredientListElement>> {
        @Override
        public ImmutableList<IIngredientListElement> load(final String filterText) throws Exception {
            if (filterText.length() == 0) {
                return baseList;
            }

            // Recursive.
            // Find a cached filter that is before the one we want, so we don't have to filter the full item list.
            // For example, the "", "i", "ir", and "iro" filters contain everything in the "iron" filter and more.
            String prevFilterText = filterText.substring(0, filterText.length() - 1);

            ImmutableList<IIngredientListElement> baseItemSet = filteredItemMapsCache.get(prevFilterText);

            FilterPredicate filterPredicate = new FilterPredicate(filterText);

            ImmutableList.Builder<IIngredientListElement> builder = ImmutableList.builder();
            baseItemSet.stream().filter(filterPredicate::apply).forEachOrdered(builder::add);
            return builder.build();
        }
    }
}
