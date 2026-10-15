package me.towdium.jecharacters.uitest.input;

import java.util.concurrent.atomic.AtomicLong;

/**
 * How many times the server has re-seated the local player.
 *
 * <p>{@code ClientboundPlayerPositionPacket} is how a server answers a client whose reported position
 * it does not agree with — the snap a player feels as rubber-banding while walking. The harness counts
 * every one it receives ({@link me.towdium.jecharacters.mixin.uitest.ClientPacketListenerMixin}), read
 * from the client thread, so a scenario that walks can say whether a stutter came from the input path
 * or from the server disagreeing about where the player is.
 *
 * <p>A plain growing count rather than a timestamp: what a scenario needs is "did one arrive between
 * these two frames", and a monotonic counter answers that without a clock.
 */
public final class PositionCorrections {

    private static final AtomicLong corrections = new AtomicLong();

    private PositionCorrections() {}

    /** Records one position correction, called from the packet handler. */
    public static void note() {
        corrections.incrementAndGet();
    }

    /** Every position correction this client has received this run. */
    public static long count() {
        return corrections.get();
    }
}
