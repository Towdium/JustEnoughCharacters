package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.match.MatchRecorder;
import me.towdium.jecharacters.utils.Match;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code Match} leg of the match recorder: every static comparison JECh's ASM adapters are
 * rewritten onto reports what it compared and what it answered.
 *
 * <p>The eight descriptors below are exactly the call sites
 * {@code me.towdium.jecharacters.asm.Transformer} rewrites Java's own string and regex searches
 * onto ({@code transformContains}, {@code transformEquals}, {@code transformStartsWith},
 * {@code transformRegExp}). {@code rank} is hooked with them because it is the ranking variant of
 * the same comparison, so the whole boolean/ranking surface of the class is covered.
 *
 * <p><b>One injection per method, at {@code RETURN}.</b> The brief for this tap asks for the query
 * and candidate "at the method head" and the boolean "at the return"; both halves are read from the
 * return frame instead, because at that point the arguments are still in scope <em>and</em> the
 * answer is, so a record is written atomically. Pairing a head injection with a return injection
 * would need per-thread state, and that state goes wrong the moment a call nests (which these do:
 * {@code matcher} calls {@code matches}, and {@code contains(CharSequence, CharSequence)} delegates
 * to {@code contains(String, CharSequence)}) or a query throws — a stale frame would silently
 * mis-attribute the next answer. Nothing here writes a record for a call that threw, which is the
 * honest outcome: the comparison has no answer.
 *
 * <p>Recording is off unless a harness run turned it on, and each handler's first statement is the
 * check that keeps a normal client at one static read per call.
 */
@Mixin(Match.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod"}) // handlers run from injected bytecode
public abstract class MatchMixin {

    @Inject(method = "contains(Ljava/lang/String;Ljava/lang/CharSequence;)Z", at = @At("RETURN"))
    private static void jechtestContains(String s, CharSequence cs, CallbackInfoReturnable<Boolean> info) {
        MatchRecorder.record("contains", s, text(cs), info.getReturnValueZ());
    }

    @Inject(method = "contains(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z", at = @At("RETURN"))
    private static void jechtestContainsSeq(
        CharSequence a,
        CharSequence b,
        CallbackInfoReturnable<Boolean> info
    ) {
        MatchRecorder.record("containsSeq", text(a), text(b), info.getReturnValueZ());
    }

    @Inject(method = "contains(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z", at = @At("RETURN"))
    private static void jechtestContainsCase(
        CharSequence a,
        CharSequence b,
        boolean ignoreCase,
        CallbackInfoReturnable<Boolean> info
    ) {
        MatchRecorder.record(ignoreCase ? "containsIgnoreCase" : "containsSeq", text(a), text(b),
                info.getReturnValueZ());
    }

    @Inject(method = "equals(Ljava/lang/String;Ljava/lang/Object;)Z", at = @At("RETURN"))
    private static void jechtestEquals(String s, Object o, CallbackInfoReturnable<Boolean> info) {
        MatchRecorder.record("equals", s, o == null ? null : o.toString(), info.getReturnValueZ());
    }

    @Inject(method = "matches(Ljava/lang/String;Ljava/lang/String;)Z", at = @At("RETURN"))
    private static void jechtestMatches(String s1, String s2, CallbackInfoReturnable<Boolean> info) {
        MatchRecorder.record("matches", s1, s2, info.getReturnValueZ());
    }

    @Inject(method = "startsWith(Ljava/lang/String;Ljava/lang/String;)Z", at = @At("RETURN"))
    private static void jechtestStartsWith(String s, String prefix, CallbackInfoReturnable<Boolean> info) {
        MatchRecorder.record("startsWith", s, prefix, info.getReturnValueZ());
    }

    @Inject(method = "startsWith(Ljava/lang/String;Ljava/lang/String;I)Z", at = @At("RETURN"))
    private static void jechtestStartsWithAt(
        String s,
        String prefix,
        int offset,
        CallbackInfoReturnable<Boolean> info
    ) {
        MatchRecorder.record("startsWithAt", s, prefix, info.getReturnValueZ());
    }

    @Inject(method = "rank(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)I", at = @At("RETURN"))
    private static void jechtestRank(
        Object o,
        String s1,
        String s2,
        CallbackInfoReturnable<Integer> info
    ) {
        MatchRecorder.record("rank", s1, s2, info.getReturnValueI() != 0);
    }

    /**
     * The regex adapter's tap.
     *
     * <p>{@code Match.matcher} answers with one of two shared matchers — {@code "a"} for a hit,
     * {@code ""} for a miss — so the caller's own {@code find()}/{@code matches()} on it is the
     * boolean. That answer is read off the matcher's region, which is a plain getter: probing with
     * {@code matches()} would consume the very state the caller is about to read.
     */
    @Inject(method = "matcher(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;",
            at = @At("RETURN"))
    private static void jechtestMatcher(
        Pattern test,
        CharSequence name,
        CallbackInfoReturnable<Matcher> info
    ) {
        Matcher returned = info.getReturnValue();
        boolean result = returned != null && returned.regionEnd() > returned.regionStart();
        MatchRecorder.record("matcher", test == null ? null : test.pattern(), text(name), result);
    }

    /** The text a sequence carries, or {@code null} for a null sequence. */
    private static String text(CharSequence sequence) {
        return sequence == null ? null : sequence.toString();
    }
}
