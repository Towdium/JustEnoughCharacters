package me.towdium.jecharacters.uitest.api;

/**
 * One rendered frame's camera reading: what the game was actually looking along when it drew that
 * frame, next to the raw and the interpolated rotation it was built from.
 *
 * <p>A scripted camera's smoothness is a property of the angle the engine <em>draws</em>, not of the
 * angle a scenario wrote. For the local player those are not the same number: the first-person camera
 * is posed from {@code Entity.getViewYRot/getViewXRot}, which {@code LocalPlayer} answers with the
 * un-interpolated {@code getYRot()/getXRot()}, so a frame's drawn angle is {@link #cameraYaw}. The
 * two neighbours are recorded beside it so a trace can tell which path an artefact came from:
 * {@link #rawYaw} is the entity field the mouse-look path writes, and {@link #viewYaw} is the
 * {@code Mth.lerp(partialTick, yRotO, yRot)} the entity-interpolation convention would produce.
 *
 * <p>{@link #wallNanos} is the frame's own wall-clock stamp ({@code System.nanoTime()}), and
 * {@link #gameTick} is the run's client-tick counter at that frame — advanced once per
 * {@code ClientTickEvent.Post}, which is the client's own tick clock rather than the level's
 * server-synced game time. Together they turn a per-frame trace into a wall-time rate and make a
 * tick boundary visible.
 *
 * <p><b>Port note (Java 8).</b> The 1.21.1 record of the same name, as a plain immutable class: Java
 * 8 has no records, so the fields are final, the accessors are the record components' names and
 * the public constructor is the canonical one. Nothing else changes.
 */
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
