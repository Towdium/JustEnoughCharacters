package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.JechSearchStorage;
import me.towdium.jecharacters.uitest.match.MatchRecorder;
import me.towdium.pinin.searchers.TreeSearcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * The PinIn leg of the match recorder: what JECh's JEI integration searched with, and what came back.
 *
 * <p>Hooking {@code Match} alone is not enough to see a JEI search. JECh's JEI plugin replaces JEI's
 * search storage with {@code JechSearchStorage}, which searches a PinIn {@code TreeSearcher} built
 * from {@code Match.context} — a tree that walks its own suffix structure and never calls back into
 * {@code Match.contains}. The repository's own {@code FakeTree}/{@code FakeArray} (the ASM-rewritten
 * vanilla search) are the same story. JECh's own storage is therefore where a JEI query can be seen.
 *
 * <p>Two taps, both inside JECh's own class rather than inside PinIn:
 *
 * <ul>
 *   <li>{@code put} remembers which key each value was registered under. A tree returns the
 *       <em>values</em> it was given, not the strings they were registered under, and the string is
 *       the thing an assertion is about (a Chinese item name). Registration is not recorded as a
 *       comparison — putting a key into a tree is not a match — it only makes the comparison's
 *       candidate readable.</li>
 *   <li>the {@code TreeSearcher.search} call inside {@code getSearchResults} records the query and
 *       the values it returned.</li>
 * </ul>
 *
 * <p>Both taps sit on JECh's own class on purpose. PinIn is a plain library on the *legacy*
 * classpath, and FML's mixin transformer only rewrites classes it loads itself — Minecraft's and the
 * loaded mods' — so a {@code @Mixin} into {@code me.towdium.pinin.searchers.TreeSearcher} is prepared
 * and then silently never applied (measured: with {@code -Dmixin.debug.export=true} the exported
 * classes contain {@code me/towdium/jecharacters/utils/Match.class} but nothing under
 * {@code me/towdium/pinin/}, and the recorder stayed empty through a real JEI search). The call site
 * inside JECh's class carries exactly the same two facts and is rewritten reliably.
 *
 * <p>{@code getAllElements} searches the empty string and is deliberately not tapped by name: it is
 * not a query, and recording it would put an "everything matched" line in front of every assertion.
 */
@Mixin(JechSearchStorage.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod"}) // handlers run from injected bytecode
public abstract class JechSearchStorageMixin {

    @Inject(method = "put(Ljava/lang/String;Ljava/lang/Object;)V", at = @At("HEAD"))
    private void jechtestRememberKey(String key, Object value, CallbackInfo info) {
        MatchRecorder.rememberKey(value, key);
    }

    /** Runs the storage's own search and records the query with the values it returned. */
    @Redirect(
        method = "getSearchResults(Ljava/lang/String;Ljava/util/function/Consumer;)V",
        at = @At(
            value = "INVOKE",
            target = "Lme/towdium/pinin/searchers/TreeSearcher;search(Ljava/lang/String;)Ljava/util/List;"
        )
    )
    private List<?> jechtestRecordSearch(TreeSearcher<?> tree, String token) {
        List<?> results = tree.search(token);
        MatchRecorder.recordSearch(token, results);
        return results;
    }
}
