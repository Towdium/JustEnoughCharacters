package me.towdium.jecharacters.mixin;

import me.towdium.jecharacters.JustEnoughCharacters;
import me.towdium.jecharacters.config.JechConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.towdium.jecharacters.JustEnoughCharacters.printMessage;
import static me.towdium.jecharacters.config.JechConfig.Spell.QUANPIN;

@Mixin(ClientLevel.class)
public class MixinClientLevel {

    @Inject(method = "addEntity", at = @At("HEAD"))
    private void injectAddEntity(Entity entity, CallbackInfo ci) {
        if (entity instanceof Player
                && JechConfig.enableChat && !JustEnoughCharacters.messageSent
                && (JechConfig.enumKeyboard == QUANPIN)
                && Minecraft.getInstance().options.languageCode.equals("zh_tw")) {
            printMessage("jecharacters.chat.taiwan");
            JustEnoughCharacters.messageSent = true;
        }
    }

}
