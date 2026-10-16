package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.input.PlayerEmbodiment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
public abstract class MinecraftMixin {

    @Inject(method = "isWindowActive()Z", at = @At("HEAD"), cancellable = true)
    private void jechtestForceWindowActiveWhileEmbodied(CallbackInfoReturnable<Boolean> info) {
        if (PlayerEmbodiment.isLive()) {
            info.setReturnValue(true);
        }
    }

    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    private void jechtestReleaseLookOnScreen(Screen guiScreen, CallbackInfo info) {
        if (guiScreen != null) {
            PlayerEmbodiment.releaseLook();
        }
    }
}
