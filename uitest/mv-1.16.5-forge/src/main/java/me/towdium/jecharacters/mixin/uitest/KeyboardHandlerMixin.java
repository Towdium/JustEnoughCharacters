package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.input.RawInputGate;
import net.minecraft.client.KeyboardListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardListener.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
public abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"), cancellable = true)
    private void jechtestGateKeyPress(
        long window,
        int key,
        int scanCode,
        int action,
        int modifiers,
        CallbackInfo info
    ) {
        if (RawInputGate.blocksNow()) {
            info.cancel();
        }
    }

    @Inject(method = "charTyped(JII)V", at = @At("HEAD"), cancellable = true)
    private void jechtestGateCharTyped(long window, int codePoint, int modifiers, CallbackInfo info) {
        if (RawInputGate.blocksNow()) {
            info.cancel();
        }
    }
}
