package me.towdium.jecharacters.mixins.manual;

import me.towdium.jecharacters.utils.Match;
import net.minecraft.client.searchtree.PlainTextSearchTree;
import net.minecraft.client.searchtree.SuffixArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PlainTextSearchTree.class)
public interface MixinVanillaTextSearch {

    @Redirect(method = "create", at = @At(value = "NEW", target = "net/minecraft/client/searchtree/SuffixArray"))
    private static SuffixArray redirectConstructor() {
        return new Match.FakeArray();
    }

}
