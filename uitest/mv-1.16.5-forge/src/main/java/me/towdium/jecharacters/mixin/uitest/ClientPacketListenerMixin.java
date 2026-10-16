package me.towdium.jecharacters.mixin.uitest;

import me.towdium.jecharacters.uitest.input.PositionCorrections;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server-correction leg of the harness: every {@code SPlayerPositionLookPacket} the client
 * handles is counted, so a walking scenario can tell a stutter the input path caused from one the
 * server caused by re-seating the player.
 *
 * <p>The counter lives in {@link PositionCorrections}; this only notes arrival. Nothing is changed
 * about the packet or how it is handled — the handler runs unchanged after the note.
 */
@Mixin(ClientPlayNetHandler.class)
@SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"}) // handlers run from injected bytecode
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void jechtestNotePositionCorrection(SPlayerPositionLookPacket packet, CallbackInfo info) {
        PositionCorrections.note();
    }
}
