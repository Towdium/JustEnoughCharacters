package me.towdium.jecharacters.uitest;

/**
 * The dev-only harness mod's own identity.
 *
 * <p>It sits in the engine source set rather than next to the {@code @Mod} class: the frame pump in
 * the engine needs the id for its own log lines, while the {@code @Mod} entry class lives in the
 * mod's entry source set, which depends on the engine and not the other way round. That split is the
 * 1.12.2 spelling of the 1.21.1 harness's {@code UiTestConstants}; the value is the same.
 */
public final class UiTestConstants {

    /** The mod id of the dev-only UI test harness. */
    public static final String modId = "jechtest";

    /** The harness's own version string, reported in the mod list. */
    public static final String version = "1.0";

    private UiTestConstants() {}
}
