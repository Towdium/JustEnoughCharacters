package me.towdium.jecharacters.uitest.engine;

import me.towdium.jecharacters.uitest.api.RunConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jspecify.annotations.Nullable;

public final class UITestBootstrap {

    private static @Nullable UITestRunner runner;

    private UITestBootstrap() {}

    public static @Nullable UITestRunner runner() {
        return runner;
    }

    static void install(UITestRunner interactive) {
        runner = interactive;
    }

    public static void onFrame() {
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

    public static void onTick() {
        if (FMLEnvironment.production) return;
        UITestRunner current = runner;
        if (current == null) return;
        current.onTick();
    }
}
