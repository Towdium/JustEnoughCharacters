package me.towdium.jecharacters.uitest;

import me.towdium.jecharacters.uitest.capture.CapturePlatform;
import me.towdium.jecharacters.uitest.capture.CapturePlatformImpl;
import me.towdium.jecharacters.uitest.input.ScreenInput;
import me.towdium.jecharacters.uitest.input.ScreenInputImpl;
import me.towdium.jecharacters.uitest.platform.ClientPlatform;
import me.towdium.jecharacters.uitest.platform.ClientPlatformImpl;
import me.towdium.jecharacters.uitest.platform.InputPlatform;
import me.towdium.jecharacters.uitest.platform.NeoForgeInputPlatform;
import me.towdium.jecharacters.uitest.platform.NeoForgePlatform;
import me.towdium.jecharacters.uitest.platform.Platform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(UiTestConstants.modId)
public final class UiTest {

    public UiTest(Dist dist) {
        if (dist.isClient()) {
            Platform.register(new NeoForgePlatform());
            InputPlatform.register(new NeoForgeInputPlatform());
            ClientPlatform.register(new ClientPlatformImpl());
            CapturePlatform.register(new CapturePlatformImpl());
            ScreenInput.register(new ScreenInputImpl());
            NeoForge.EVENT_BUS.addListener(UiTestCommands::register);
        }
    }
}
