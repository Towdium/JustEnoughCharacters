package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.input.CursorState;
import me.towdium.jecharacters.uitest.input.PlayerEmbodiment;
import me.towdium.jecharacters.uitest.input.RawInputGate;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The pointer leg of the UI-test harness. Applied always; the behaviour is installed at runtime
 * by putting a {@link CursorState} source in place and raising the {@link RawInputGate}.
 *
 * <p>{@code xpos()/ypos()} are the single source of truth for every pointer coordinate vanilla
 * produces — the mouse arguments of {@code Screen.mouseClicked/mouseReleased}, the mouse
 * arguments {@code Screen.render} receives, and CloudLib's own {@code ScreenUtil.getMousePos()}
 * reads — so overriding those two getters moves the whole pipeline at once. The override is a
 * core {@code @Inject} at RETURN rather than MixinExtras' {@code @ModifyReturnValue}, because
 * MixinExtras is not core Mixin and this build's compile classpath carries only
 * {@code net.fabricmc:sponge-mixin}, where {@code org.spongepowered.asm.mixin.injection} has no
 * {@code ModifyReturnValue} at all.
 *
 * <p>{@code grabMouse} is cancelled while a source is installed or a player embodiment is live:
 * {@code Minecraft.setScreen(null)} between scenarios would otherwise re-grab the pointer, and an
 * embodied run sets the grabbed flag directly ({@link PlayerEmbodiment#takeLook}) instead of letting
 * vanilla warp the real cursor into the window centre. Cancelling at HEAD leaves
 * {@code mouseGrabbed} alone, which keeps {@code releaseMouse} — whose body is inside
 * {@code if (mouseGrabbed)} — consistent.
 *
 * <p>{@code onMove} is gated too, because {@code handleAccumulatedMovement} dispatches from the
 * raw {@code xpos/ypos} <em>fields</em>, which the getters do not cover; gating there also keeps
 * the accumulated delta from growing, so that block never runs at all during a run.
 *
 * <p>Handler methods are named without the usual {@code $} separator: the repository's coding-style
 * gate rejects dollar signs in method names, and the {@code jechtest} prefix already keeps them
 * clear of anything the target class declares.
 */
@Mixin(MouseHandler.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"}) // handlers run from injected bytecode
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
