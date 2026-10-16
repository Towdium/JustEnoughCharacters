package me.towdium.jecharacters.uitest;

/**
 * The dev-only harness mod's own identity.
 *
 * <p>It sits in the engine source set rather than next to the {@code @Mod} class: the frame pump in
 * the engine needs the id for its {@code @EventBusSubscriber}, while the entry class lives in the
 * mod's entry source set, which depends on the engine and not the other way round.
 */
public final class UiTestConstants {

    /** The mod id of the dev-only UI test harness. */
    public static final String modId = "jechtest";

    private UiTestConstants() {}
}
