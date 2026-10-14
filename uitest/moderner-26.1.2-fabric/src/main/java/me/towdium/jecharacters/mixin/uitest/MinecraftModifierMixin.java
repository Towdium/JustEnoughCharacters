package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.input.KeyState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The modifier leg of the UI-test harness: while a {@link KeyState} source is installed, the game's
 * own "is Ctrl/Shift/Alt held" reads answer from it instead of querying GLFW against the live
 * keyboard.
 *
 * <p>Modifier state is not part of any input event payload. A screen reads the modifiers off the
 * event it was handed ({@code MouseButtonEvent.hasShiftDown()} and friends), which the harness's
 * driver fills in — but everything that is <em>not</em> a screen event reads the keyboard instead,
 * and that is where a scripted chord would otherwise be invisible. The case that matters here is a
 * mod bind declared with a modifier: JEI's {@code key.jei.focusSearch} is Ctrl/Cmd+F, and the JEI and
 * NeoForge sides of that check both end in {@code Minecraft.hasControlDown()}
 * ({@code JeiKeyModifier.CONTROL_OR_COMMAND.isActive()} and
 * {@code KeyModifier.CONTROL.isActive()}). Overriding these three reads is what makes the harness's
 * "hold the modifier, press the key" a real chord rather than a no-op.
 *
 * <p><b>Port note (MC 1.21.10).</b> This is the 1.21.1 {@code ScreenModifierMixin} at its new home.
 * It injected the same three questions into {@code Screen.hasControlDown()/hasShiftDown()/
 * hasAltDown()}, the statics every modifier read funnelled through then; those statics are gone, and
 * the reads that replaced them — vanilla's own, JEI's and NeoForge's alike — are the three methods
 * on {@link Minecraft} below. Same guard, same three answers, one class over.
 *
 * <p>Only the three primitives are touched: {@code InputWithModifiers}' {@code isCut/isPaste/isCopy/
 * isSelectAll} are built on an event's own mask, and {@code Minecraft.hasControlDown} itself is what
 * {@code KeyModifier.CONTROL.isActive()} calls, so both inherit the override without a second
 * injection point.
 *
 * <p>The override is applied only while a source is installed; without one the methods keep
 * vanilla's own GLFW answer, so a plain dev client is unaffected even though this config is
 * compiled into the harness mod.
 */
@Mixin(Minecraft.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod"}) // handlers run from injected bytecode
public abstract class MinecraftModifierMixin {

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
