package me.towdium.jecharacters.uitest.engine;

import me.towdium.jecharacters.uitest.UiTestConstants;
import me.towdium.jecharacters.uitest.api.RunConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jspecify.annotations.Nullable;

/**
 * The frame and tick pump.
 *
 * <p>It subscribes to {@link RenderFrameEvent.Post} rather than to a screen render event on purpose:
 * a screen event stops firing the moment no screen is open, which is exactly the state right after
 * a world loads — the one the run most needs frames in. The event fires on the render thread once
 * per frame, whether or not a world, a screen or a focused window exists.
 *
 * <p>{@link ClientTickEvent.Post} is the other half: the game's own clock, advanced once per client
 * tick, which is the unit a camera sweep is measured in. Counting it here rather than reading the
 * level's game time matters because the level's time is synced from the server and jitters with
 * packet arrival, while the tick event fires exactly once per local tick.
 *
 * <p>The subscriber belongs to {@code jechtest}, so it is only ever registered when the
 * harness mod is loaded — a plain dev client never reaches this class at all. On top of that the
 * class returns immediately in production, and a run only starts when {@code -Djech.uitest.run}
 * was set.
 */
@EventBusSubscriber(modid = UiTestConstants.modId, value = Dist.CLIENT)
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

    @SubscribeEvent
    @SuppressWarnings("PMD.UnusedFormalParameter") // the bus signature requires the event parameter
    public static void onFrameRendered(RenderFrameEvent.Post event) {
        if (FMLEnvironment.production) return;
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
    @SubscribeEvent
    @SuppressWarnings("PMD.UnusedFormalParameter") // the bus signature requires the event parameter
    public static void onClientTick(ClientTickEvent.Post event) {
        if (FMLEnvironment.production) return;
        UITestRunner current = runner;
        if (current == null) return;
        current.onTick();
    }
}
