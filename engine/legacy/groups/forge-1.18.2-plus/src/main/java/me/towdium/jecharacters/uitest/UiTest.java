package me.towdium.jecharacters.uitest;

import me.towdium.jecharacters.uitest.capture.CapturePlatform;
import me.towdium.jecharacters.uitest.capture.CapturePlatformImpl;
import me.towdium.jecharacters.uitest.input.ScreenInput;
import me.towdium.jecharacters.uitest.input.ScreenInputImpl;
import me.towdium.jecharacters.uitest.platform.ClientPlatform;
import me.towdium.jecharacters.uitest.platform.ClientPlatformImpl;
import me.towdium.jecharacters.uitest.platform.ForgeInputPlatform;
import me.towdium.jecharacters.uitest.platform.ForgePlatform;
import me.towdium.jecharacters.uitest.platform.InputPlatform;
import me.towdium.jecharacters.uitest.platform.Platform;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLEnvironment;

@net.minecraftforge.fml.common.Mod(UiTestConstants.modId)
public final class UiTest {

    public UiTest() {
        if (FMLEnvironment.dist.isClient()) {
            Platform.register(new ForgePlatform());
            InputPlatform.register(new ForgeInputPlatform());
            ClientPlatform.register(new ClientPlatformImpl());
            CapturePlatform.register(new CapturePlatformImpl());
            ScreenInput.register(new ScreenInputImpl());
            MinecraftForge.EVENT_BUS.addListener(UiTestCommands::register);
        }
    }
}
