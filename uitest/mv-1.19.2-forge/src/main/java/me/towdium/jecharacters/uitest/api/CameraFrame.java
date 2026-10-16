package me.towdium.jecharacters.uitest.api;

public record CameraFrame(
    long wallNanos,
    long gameTick,
    float partialTick,
    float rawYaw,
    float rawPitch,
    float viewYaw,
    float viewPitch,
    float cameraYaw,
    float cameraPitch
) {}
