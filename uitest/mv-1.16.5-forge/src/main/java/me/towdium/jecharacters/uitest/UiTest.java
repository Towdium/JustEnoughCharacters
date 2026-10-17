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
import me.towdium.jecharacters.uitest.platform.ServerPlatform;
import me.towdium.jecharacters.uitest.platform.ServerPlatformImpl;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@net.minecraftforge.fml.common.Mod(UiTestConstants.modId)
public final class UiTest {

    private static final Logger log = LoggerFactory.getLogger("jechtest-uitest");

    public UiTest() {
        if (FMLEnvironment.dist.isClient()) {
            Platform.register(new ForgePlatform());
            InputPlatform.register(new ForgeInputPlatform());
            ClientPlatform.register(new ClientPlatformImpl());
            CapturePlatform.register(new CapturePlatformImpl());
            ScreenInput.register(new ScreenInputImpl());
            ServerPlatform.register(new ServerPlatformImpl());
            log.info("jechtest harness loaded on the client");
        }
    }
}
