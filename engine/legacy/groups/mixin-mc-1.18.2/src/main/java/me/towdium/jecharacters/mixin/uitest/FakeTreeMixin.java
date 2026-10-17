package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.match.MatchRecorder;
import me.towdium.jecharacters.utils.FakeTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Set;

@Mixin(value = FakeTree.class, remap = false)
@SuppressWarnings({"PMD.UnusedPrivateMethod"})
public abstract class FakeTreeMixin {

    @Inject(method = "put(Ljava/lang/String;Ljava/lang/Object;)V", at = @At("HEAD"))
    private void jechtestRememberKey(String key, Object value, CallbackInfo info) {
        MatchRecorder.rememberKey(value, key);
    }

    @Inject(method = "getSearchResults(Ljava/lang/String;Ljava/util/Set;)V", at = @At("RETURN"))
    private void jechtestRecordSearch(String word, Set<?> results, CallbackInfo info) {
        MatchRecorder.recordSearch(word, new ArrayList<>(results));
    }
}
