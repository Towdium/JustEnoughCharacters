package me.towdium.jecharacters.mixin.uitest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import me.towdium.jecharacters.uitest.input.KeyState;
import me.towdium.jecharacters.uitest.input.PlayerEmbodiment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InputConstants.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
public abstract class InputConstantsMixin {

    @Inject(method = "isKeyDown(Lcom/mojang/blaze3d/platform/Window;I)Z", at = @At("HEAD"), cancellable = true)
    private static void jechtestVirtualHeldKey(Window window, int key, CallbackInfoReturnable<Boolean> info) {
        if (PlayerEmbodiment.isLive() && KeyState.isInstalled()) {
            info.setReturnValue(KeyState.isKeyDown(key));
        }
    }
}
