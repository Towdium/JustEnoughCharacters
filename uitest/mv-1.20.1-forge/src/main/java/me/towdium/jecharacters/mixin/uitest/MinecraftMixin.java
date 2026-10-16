package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.input.PlayerEmbodiment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The client-state leg of the in-world harness: the window counts as focused while a player
 * embodiment is live, and opening a screen releases the camera's mouse-look.
 *
 * <p>{@code isWindowActive()} is vanilla's window-focus flag. Everything the harness needs for
 * scripted camera movement sits behind it: {@code MouseHandler.onMove} only accumulates pointer
 * deltas while it is true, and {@code handleAccumulatedMovement} — the per-frame turn pump — returns
 * early without it ({@code MouseHandler.java:251}, {@code :266}). A dev run is a real window that the
 * user may well have switched away from, and a hidden multi-process client is never focused at all,
 * so an embodied run answers {@code true} here for as long as the run owns the camera. The flag is
 * not written (a GLFW focus event would overwrite it); the getter is answered instead.
 *
 * <p>The one other consumer is {@code GameRenderer.render}, which pauses the game on focus loss when
 * {@code pauseOnLostFocus} is set. Answering {@code true} takes the branch that refreshes
 * {@code lastActiveTime}, so the override can only prevent a spurious pause, never cause one. The
 * harness pins {@code pauseOnLostFocus} to {@code false} anyway.
 *
 * <p>{@code setScreen} is gated so that a screen opening during a live run behaves like a real
 * player's: {@link PlayerEmbodiment#releaseLook} clears {@code mouseGrabbed} before vanilla's
 * {@code releaseMouse} runs, which both stops the turn pump (a real client cannot turn the camera
 * while a screen is up) and keeps {@code releaseMouse}'s pointer warp from firing.
 */
@Mixin(Minecraft.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"}) // handlers run from injected bytecode
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
