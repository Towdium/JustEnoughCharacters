package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.engine.UITestBootstrap;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod"})
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
