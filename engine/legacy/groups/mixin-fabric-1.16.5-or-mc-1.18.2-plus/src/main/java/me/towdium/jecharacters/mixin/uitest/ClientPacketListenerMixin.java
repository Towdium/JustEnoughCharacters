package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.input.PositionCorrections;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void jechtestNotePositionCorrection(ClientboundPlayerPositionPacket packet, CallbackInfo info) {
        PositionCorrections.note();
    }
}
