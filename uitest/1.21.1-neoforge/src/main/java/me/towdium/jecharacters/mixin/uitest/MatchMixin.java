package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.match.MatchRecorder;
import me.towdium.jecharacters.utils.Match;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(Match.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod"})
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

    private static String text(CharSequence sequence) {
        return sequence == null ? null : sequence.toString();
    }
}
