package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.input.CursorState;
import me.towdium.jecharacters.uitest.input.PlayerEmbodiment;
import me.towdium.jecharacters.uitest.input.RawInputGate;
import net.minecraft.client.MouseHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHelper.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
public abstract class MouseHandlerMixin {

    @Inject(method = "xpos()D", at = @At("RETURN"), cancellable = true)
    private void jechtestVirtualCursorX(CallbackInfoReturnable<Double> info) {
        if (CursorState.isInstalled()) {
            info.setReturnValue(CursorState.toPhysicalX(CursorState.cursorX()));
        }
    }

    @Inject(method = "ypos()D", at = @At("RETURN"), cancellable = true)
    private void jechtestVirtualCursorY(CallbackInfoReturnable<Double> info) {
        if (CursorState.isInstalled()) {
            info.setReturnValue(CursorState.toPhysicalY(CursorState.cursorY()));
        }
    }

    @Inject(method = "grabMouse()V", at = @At("HEAD"), cancellable = true)
    private void jechtestSkipGrabWhileVirtual(CallbackInfo info) {
        if (CursorState.isInstalled() || PlayerEmbodiment.isLive()) {
            info.cancel();
        }
    }

    @Inject(method = "onMove(JDD)V", at = @At("HEAD"), cancellable = true)
    private void jechtestGateMove(long window, double x, double y, CallbackInfo info) {
        if (RawInputGate.blocksNow()) {
            info.cancel();
        }
    }

    @Inject(method = "onPress(JIII)V", at = @At("HEAD"), cancellable = true)
    private void jechtestGatePress(long window, int button, int action, int modifiers, CallbackInfo info) {
        if (RawInputGate.blocksNow()) {
            info.cancel();
        }
    }

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void jechtestGateScroll(long window, double xOffset, double yOffset, CallbackInfo info) {
        if (RawInputGate.blocksNow()) {
            info.cancel();
        }
    }
}
