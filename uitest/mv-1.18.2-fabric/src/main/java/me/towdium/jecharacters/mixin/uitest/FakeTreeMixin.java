package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.match.MatchRecorder;
import me.towdium.jecharacters.utils.FakeTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Set;

/**
 * The PinIn leg of the match recorder on this generation: what JECh's JEI integration searched with,
 * and what came back.
 *
 * <p>Hooking {@code Match} alone is not enough to see a JEI search. JEI's own ingredient filter
 * searches a suffix tree, and this line's JECh replaces that tree with its own {@code FakeTree}, a
 * {@code GeneralizedSuffixTree} that searches a PinIn {@code TreeSearcher} built from
 * {@code Match.context} — a tree that walks its own suffix structure and never calls back into
 * {@code Match.contains}. The tap is therefore the tree's own two entry points.
 *
 * <ul>
 *   <li>{@code put} remembers which key each value was registered under. A tree returns the
 *       <em>values</em> it was given, not the strings they were registered under, and the string is
 *       the thing an assertion is about (an item's Chinese name). Registration is not recorded as a
 *       comparison — putting a key into a tree is not a match — it only makes the comparison's
 *       candidate readable.</li>
 *   <li>{@code getSearchResults} records the query and the values the tree returned for it.</li>
 * </ul>
 *
 * <p>{@code getAllElements} searches the empty string and is deliberately not tapped by name: it is
 * not a query, and recording it would put an "everything matched" line in front of every assertion.
 *
 * <p><b>Port note (1.19.2 sibling → 1.18.2 fabric): why this tap is {@code FakeTree} and not
 * {@code JechSearchStorage}.</b> The 1.19.2 copy taps JECh's {@code JechSearchStorage}, which exists
 * on that generation because JECh ships a JEI plugin there ({@code JechJeiPlugin}, the jar's own
 * class, logging "Registering JEI pinyin search storage" as it installs the storage). This
 * generation's JECh jar carries no JEI plugin class and no storage class at all (measured: {@code
 * unzip -l jecharacters-1.18.2-fabric-4.6.12.jar} lists neither). Its JEI bridge is instead the
 * fabric transformer entrypoint {@code jech:transformer} →
 * {@code me.towdium.jecharacters.transformers.JeiTransformer}, which rewrites JECh's own targets
 * {@code mezz/jei/common/search/ElementPrefixParser} (swapping {@code GeneralizedSuffixTree}'s
 * constructor for {@code FakeTree}'s in the parser's {@code <clinit>} and in the constructor that
 * takes {@code RegisteredIngredients}, {@code IIngredientFilterConfig}) and
 * {@code mezz/jei/common/ingredients/IngredientFilter.parseSearchTokens} (wrapping the argument of
 * {@code Pattern.matcher} with {@code Match.wrap}, a quoting helper rather than a comparison). So
 * the search itself happens inside the class tapped here, there is no storage class to tap, and this
 * line has no "Registering JEI pinyin search storage" marker for a log check — while {@code Match}
 * alone would see a JEI search only through that quoting helper, never as a comparison. The sibling
 * {@code mv-1.18.2-forge} copy taps the same class for the same reason (its jar reaches {@code
 * FakeTree} through the legacy ASM coremod {@code coremods/jei_transformer.js}).
 *
 * <p>{@code remap = false} on the class: the target is the mod under test's own class, which this
 * build has no obfuscation mapping for, and the descriptors here carry only JECh/PinIn/JDK types.
 */
@Mixin(value = FakeTree.class, remap = false)
@SuppressWarnings({"PMD.UnusedPrivateMethod"}) // handlers run from injected bytecode
public abstract class FakeTreeMixin {

    @Inject(method = "put(Ljava/lang/String;Ljava/lang/Object;)V", at = @At("HEAD"))
    private void jechtestRememberKey(String key, Object value, CallbackInfo info) {
        MatchRecorder.rememberKey(value, key);
    }

    /** Records the query with the values the tree returned for it. */
    @Inject(method = "getSearchResults(Ljava/lang/String;Ljava/util/Set;)V", at = @At("RETURN"))
    private void jechtestRecordSearch(String word, Set<?> results, CallbackInfo info) {
        MatchRecorder.recordSearch(word, new ArrayList<>(results));
    }
}
