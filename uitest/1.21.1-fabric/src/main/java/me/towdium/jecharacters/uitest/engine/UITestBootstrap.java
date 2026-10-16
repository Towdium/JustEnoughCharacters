package me.towdium.jecharacters.uitest.engine;

import me.towdium.jecharacters.uitest.api.RunConfig;
import org.jspecify.annotations.Nullable;

/**
 * The frame and tick pump.
 *
 * <p>Both halves are driven from {@link me.towdium.jecharacters.mixin.uitest.MinecraftPumpMixin},
 * which sits on {@code Minecraft}'s own tick and render-tick methods: that is the one hook that fires
 * once per frame whether or not a world, a screen or a focused window exists, which is exactly the
 * state right after a world loads — the one the run most needs frames in. A screen event is
 * deliberately not used: it stops firing the moment no screen is open. The tick half is the game's
 * own clock, advanced once per client tick, which is the unit a camera sweep is measured in;
 * counting it here rather than reading the level's game time matters because the level's time is
 * synced from the server and jitters with packet arrival, while a client tick is a local tick.
 *
 * <p><b>Port note (NeoForge → Fabric).</b> The NeoForge copy of this class is an
 * {@code @EventBusSubscriber} on {@code RenderFrameEvent.Post} and {@code ClientTickEvent.Post}.
 * Fabric has no always-firing frame event (its {@code ClientTickEvents} only fire on ticks, and the
 * HUD/render callbacks only fire with a level loaded), so the same two moments are taken from vanilla
 * itself, through the harness's own mixin config. Everything below that seam is identical.
 *
 * <p>The mixin belongs to {@code jechtest}, so it is only ever applied when the harness mod is
 * loaded — a plain client never reaches this class at all, and the shipped jar carries neither the
 * mixin nor this class. On top of that a run only starts when {@code -Djech.uitest.run} was set.
 *
 * <p><b>Why {@code -Djech.uitest.run} is the whole gate.</b> It used to be that plus "the loader
 * says this is a development environment", because the harness was loaded onto a Gradle dev run's
 * class path. The harness is now a production artifact loaded through the game's own {@code mods/}
 * directory — that is the point of the direct launcher — so there is no development environment to
 * detect, and the dev check did not merely become redundant, it made every scenario silently
 * unattended: the mod loaded, the property was set, and this method returned before starting
 * anything.
 */
public final class UITestBootstrap {

    private static @Nullable UITestRunner runner;

    private UITestBootstrap() {}

    /** The run driving this client, or {@code null} when nothing was requested. */
    public static @Nullable UITestRunner runner() {
        return runner;
    }

    /**
     * Hands the frame pump a run started from inside the game ({@code /jechtest run}), which
     * never went through {@link RunConfig#requested()}. Replacing a finished runner is intended: the
     * command refuses to start a second live run itself.
     */
    static void install(UITestRunner interactive) {
        runner = interactive;
    }

    /** One rendered frame, called by the harness's own mixin on {@code Minecraft}'s render tick. */
    public static void onFrame() {
        UITestRunner current = runner;
        if (current == null) {
            if (!RunConfig.requested()) return;
            current = UITestRunner.bootstrap();
            runner = current;
            current.startWatchdog();
        }
        current.onFrame();
    }

    /**
     * One client tick. The run's sweeps are measured in ticks, so the counter has to advance on the
     * game's clock rather than on the frame clock — a sweep is then the same length in seconds
     * whatever the frame rate is doing.
     */
    public static void onTick() {
        UITestRunner current = runner;
        if (current == null) return;
        current.onTick();
    }
}
