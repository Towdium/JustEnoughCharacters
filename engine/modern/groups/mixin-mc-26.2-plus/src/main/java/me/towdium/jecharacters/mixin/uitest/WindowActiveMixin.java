package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.input.PlayerEmbodiment;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
public abstract class WindowActiveMixin {

    @Inject(method = "isWindowActive()Z", at = @At("HEAD"), cancellable = true)
    private void jechtestForceWindowActiveWhileEmbodied(CallbackInfoReturnable<Boolean> info) {
        if (PlayerEmbodiment.isLive()) {
            info.setReturnValue(true);
        }
    }
}
