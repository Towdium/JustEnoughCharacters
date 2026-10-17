package me.towdium.jecharacters.uitest.engine;

import me.towdium.jecharacters.uitest.UiTestConstants;
import me.towdium.jecharacters.uitest.api.RunConfig;
import me.towdium.jecharacters.uitest.platform.Platform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = UiTestConstants.modId, value = Dist.CLIENT)
public final class UITestBootstrap {

    private static @Nullable UITestRunner runner;

    private UITestBootstrap() {}

    public static @Nullable UITestRunner runner() {
        return runner;
    }

    static void install(UITestRunner interactive) {
        runner = interactive;
    }

    @SubscribeEvent
    @SuppressWarnings("PMD.UnusedFormalParameter")
    public static void onFrameRendered(RenderFrameEvent.Post event) {
        if (!Platform.current().developmentEnvironment()) return;
        UITestRunner current = runner;
        if (current == null) {
            if (!RunConfig.requested()) return;
            current = UITestRunner.bootstrap();
            runner = current;
            current.startWatchdog();
        }
        current.onFrame();
    }

    @SubscribeEvent
    @SuppressWarnings("PMD.UnusedFormalParameter")
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Platform.current().developmentEnvironment()) return;
        UITestRunner current = runner;
        if (current == null) return;
        current.onTick();
    }
}
