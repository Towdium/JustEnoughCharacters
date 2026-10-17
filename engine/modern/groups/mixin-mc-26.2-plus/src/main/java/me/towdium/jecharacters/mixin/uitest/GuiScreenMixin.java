package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.input.PlayerEmbodiment;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
public abstract class GuiScreenMixin {

    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    private void jechtestReleaseLookOnScreen(Screen guiScreen, CallbackInfo info) {
        if (guiScreen != null) {
            PlayerEmbodiment.releaseLook();
        }
    }
}
