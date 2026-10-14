package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.input.KeyState;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The modifier leg of the UI-test harness: while a {@link KeyState} source is installed, vanilla's
 * static modifier reads answer from it instead of querying GLFW against the live keyboard.
 *
 * <p>Modifier state is not part of any input event payload. CloudLib's {@code InputContext}
 * delegates {@code isCtrlDown}/{@code isShiftDown}/{@code isAltDown} straight to these three
 * statics, and a widget written against them is the normal case — so a scripted gesture cannot
 * express "Ctrl-click" or "Shift-click" unless the statics themselves are overridden. Overriding
 * them here is what turns {@code ScenarioBuilder.typeInto}'s Ctrl+A and its {@code shiftClick} into
 * real gestures rather than no-ops.
 *
 * <p>Only the three primitives are touched: {@code isCut/isPaste/isCopy/isSelectAll} are built on
 * {@code hasControlDown}, so they inherit the override without a second injection point.
 *
 * <p>The override is applied only while a source is installed; without one the methods keep
 * vanilla's own GLFW answer, so a plain dev client is unaffected even though this config is
 * compiled into the harness mod.
 */
@Mixin(Screen.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod"}) // handlers run from injected bytecode
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
