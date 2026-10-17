package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.input.RawInputGate;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
public abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
    private void jechtestGateKeyPress(
        long window,
        int action,
        KeyEvent event,
        CallbackInfo info
    ) {
        if (RawInputGate.blocksNow()) {
            info.cancel();
        }
    }

    @Inject(
        method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void jechtestGateCharTyped(long window, CharacterEvent event, CallbackInfo info) {
        if (RawInputGate.blocksNow()) {
            info.cancel();
        }
    }
}
