package me.towdium.jecharacters.uitest.api;

import java.util.Objects;

public final class CameraFrame {

    private final long wallNanos;

    private final long gameTick;

    private final float partialTick;

    private final float rawYaw;

    private final float rawPitch;

    private final float viewYaw;

    private final float viewPitch;

    private final float cameraYaw;

    private final float cameraPitch;

    public CameraFrame(
        long wallNanos,
        long gameTick,
        float partialTick,
        float rawYaw,
        float rawPitch,
        float viewYaw,
        float viewPitch,
        float cameraYaw,
        float cameraPitch
    ) {
        this.wallNanos = wallNanos;
        this.gameTick = gameTick;
        this.partialTick = partialTick;
        this.rawYaw = rawYaw;
        this.rawPitch = rawPitch;
        this.viewYaw = viewYaw;
        this.viewPitch = viewPitch;
        this.cameraYaw = cameraYaw;
        this.cameraPitch = cameraPitch;
    }

    public long wallNanos() {
        return wallNanos;
    }

    public long gameTick() {
        return gameTick;
    }

    public float partialTick() {
        return partialTick;
    }

    public float rawYaw() {
        return rawYaw;
    }

    public float rawPitch() {
        return rawPitch;
    }

    public float viewYaw() {
        return viewYaw;
    }

    public float viewPitch() {
        return viewPitch;
    }

    public float cameraYaw() {
        return cameraYaw;
    }

    public float cameraPitch() {
        return cameraPitch;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CameraFrame)) return false;
        CameraFrame that = (CameraFrame) other;
        return wallNanos == that.wallNanos
            && gameTick == that.gameTick
            && Float.compare(partialTick, that.partialTick) == 0
            && Float.compare(rawYaw, that.rawYaw) == 0
            && Float.compare(rawPitch, that.rawPitch) == 0
            && Float.compare(viewYaw, that.viewYaw) == 0
            && Float.compare(viewPitch, that.viewPitch) == 0
            && Float.compare(cameraYaw, that.cameraYaw) == 0
            && Float.compare(cameraPitch, that.cameraPitch) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            wallNanos, gameTick, partialTick, rawYaw, rawPitch, viewYaw, viewPitch, cameraYaw, cameraPitch);
    }

    @Override
    public String toString() {
        return "CameraFrame[wallNanos=" + wallNanos
            + ", gameTick=" + gameTick
            + ", partialTick=" + partialTick
            + ", rawYaw=" + rawYaw
            + ", rawPitch=" + rawPitch
            + ", viewYaw=" + viewYaw
            + ", viewPitch=" + viewPitch
            + ", cameraYaw=" + cameraYaw
            + ", cameraPitch=" + cameraPitch
            + "]";
    }
}
