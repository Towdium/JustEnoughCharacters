package me.towdium.jecharacters.mixin.uitest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import me.towdium.jecharacters.uitest.input.KeyState;
import me.towdium.jecharacters.uitest.input.PlayerEmbodiment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The held-key leg of the in-world harness: while a player embodiment is live, vanilla's GLFW key
 * query answers from {@link KeyState} instead of the live keyboard.
 *
 * <p>{@code KeyboardHandler.keyPress} writes the held state of every {@code KeyMapping}, and that is
 * the whole story for WASD, jump and sneak — {@code KeyboardInput.tick} reads
 * {@code options.keyUp.isDown()} and nothing else. It is not the whole story for the game's own
 * direct reads: {@code InputConstants.isKeyDown} is how {@code KeyMapping.setAll}, the debug-key
 * handling and every hand-written mod query the keyboard, and a scripted hold is invisible to it.
 * Routing it through {@link KeyState} is what makes {@code holdKey(GLFW_KEY_W)} as true as it is for
 * a real player. The modifier reads a scripted chord needs — {@code Minecraft.hasControlDown} and,
 * through it, JEI's and NeoForge's own "is this bind's modifier held" checks — are answered by
 * {@link MinecraftModifierMixin}; this seam is the raw held-key query underneath them.
 *
 * <p>Scoped to {@link PlayerEmbodiment#isLive()} rather than to "a {@link KeyState} source is
 * installed", deliberately: a screen-driving run keeps the exact keyboard semantics it has always
 * had, and only an embodied run — where the script really is holding movement keys down — widens the
 * override. Without a source installed the vanilla body runs untouched, so a plain dev client never
 * sees any of this.
 *
 * <p><b>Port note (MC 1.21.10).</b> The target now takes the {@link Window} rather than the raw GLFW
 * handle, so the descriptor changed with the signature.
 */
@Mixin(InputConstants.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"}) // handlers run from injected bytecode
public abstract class InputConstantsMixin {

    @Inject(method = "isKeyDown(Lcom/mojang/blaze3d/platform/Window;I)Z", at = @At("HEAD"), cancellable = true)
    private static void jechtestVirtualHeldKey(Window window, int key, CallbackInfoReturnable<Boolean> info) {
        if (PlayerEmbodiment.isLive() && KeyState.isInstalled()) {
            info.setReturnValue(KeyState.isKeyDown(key));
        }
    }
}
