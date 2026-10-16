package me.towdium.jecharacters.uitest.api;

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
        float cameraPitch) {
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
}
