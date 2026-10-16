package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.input.RawInputGate;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The keyboard leg of the UI-test harness: while the {@link RawInputGate} is raised, real key
 * presses and typed characters never reach the game.
 *
 * <p>Vanilla's keyboard handlers carry no window-focus guard, so a stray keystroke into a
 * backgrounded window would land in the middle of a scripted run.
 *
 * <p>Scripted in-world input (WASD holds, chat, hotbar) goes through these same two bodies: nothing
 * but {@code KeyboardHandler.keyPress} writes the {@link net.minecraft.client.KeyMapping} a
 * movement bind is read from, and nothing but {@code charTyped} reaches a chat screen's text field.
 * {@link RawInputGate#runSynthetic} is what lets those calls through while the gate stays up for
 * every real one.
 *
 * <p>Handler methods are named without the usual {@code $} separator: the repository's coding-style
 * gate rejects dollar signs in method names, and the {@code jechtest} prefix already keeps them
 * clear of anything the target class declares.
 */
@Mixin(KeyboardHandler.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"}) // handlers run from injected bytecode
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
