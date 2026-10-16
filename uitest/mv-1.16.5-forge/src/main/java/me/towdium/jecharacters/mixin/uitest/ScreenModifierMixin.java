package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.input.KeyState;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod"})
public abstract class ScreenModifierMixin {

    @Inject(method = "hasControlDown()Z", at = @At("RETURN"), cancellable = true)
    private static void jechtestVirtualControl(CallbackInfoReturnable<Boolean> info) {
        if (KeyState.isInstalled()) {
            info.setReturnValue(KeyState.isCtrlDown());
        }
    }

    @Inject(method = "hasShiftDown()Z", at = @At("RETURN"), cancellable = true)
    private static void jechtestVirtualShift(CallbackInfoReturnable<Boolean> info) {
        if (KeyState.isInstalled()) {
            info.setReturnValue(KeyState.isShiftDown());
        }
    }

    @Inject(method = "hasAltDown()Z", at = @At("RETURN"), cancellable = true)
    private static void jechtestVirtualAlt(CallbackInfoReturnable<Boolean> info) {
        if (KeyState.isInstalled()) {
            info.setReturnValue(KeyState.isAltDown());
        }
    }
}
