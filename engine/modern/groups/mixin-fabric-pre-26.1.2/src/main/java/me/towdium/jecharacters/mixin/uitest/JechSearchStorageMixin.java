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

@Mixin(value = JechSearchStorage.class, remap = false)
@SuppressWarnings({"PMD.UnusedPrivateMethod"})
public abstract class JechSearchStorageMixin {

    @Inject(method = "put(Ljava/lang/String;Ljava/lang/Object;)V", at = @At("HEAD"))
    private void jechtestRememberKey(String key, Object value, CallbackInfo info) {
        MatchRecorder.rememberKey(value, key);
    }

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
