package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.engine.UITestBootstrap;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The frame pump: the two moments {@code UITestBootstrap} needs, taken
 * from vanilla itself because this Forge generation's always-firing frame
 * event ({@code TickEvent.RenderTickEvent}) renames itself between Forge
 * versions, while {@code runTick}/{@code tick} do not.
 *
 * <ul>
 *   <li>{@code runTick(boolean)}'s tail is one rendered frame. It fires whether or not a world, a
 *       screen or a focused window exists — the state right after a world loads is the one the run
 *       most needs frames in, and it is exactly the state a screen-render callback stops firing in.</li>
 *   <li>{@code tick()}'s tail is one client tick, the game's own clock and the unit a camera sweep is
 *       measured in.</li>
 * </ul>
 *
 * <p><b>Port note (NeoForge 1.21.1 → Forge 1.20.1).</b> The NeoForge copy of the harness subscribes
 * to {@code RenderFrameEvent.Post} and {@code ClientTickEvent.Post} instead; nothing below this seam
 * differs. The mixin lives in {@code jechtest.mixins.json}, which only the harness mod declares, so a
 * plain dev client never has it applied, and the shipped jar carries neither the config nor this
 * class.
 */
@Mixin(Minecraft.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod"}) // handlers run from injected bytecode
public abstract class MinecraftPumpMixin {

    @Inject(method = "runTick(Z)V", at = @At("TAIL"), require = 1)
    private void jechtestFrame(boolean renderLevel, CallbackInfo info) {
        UITestBootstrap.onFrame();
    }

    @Inject(method = "tick()V", at = @At("TAIL"), require = 1)
    private void jechtestTick(CallbackInfo info) {
        UITestBootstrap.onTick();
    }
}
