package me.towdium.jecharacters.uitest;

import me.towdium.jecharacters.uitest.capture.CapturePlatform;
import me.towdium.jecharacters.uitest.capture.CapturePlatformImpl;
import me.towdium.jecharacters.uitest.input.ScreenInput;
import me.towdium.jecharacters.uitest.input.ScreenInputImpl;
import me.towdium.jecharacters.uitest.platform.ClientPlatform;
import me.towdium.jecharacters.uitest.platform.ClientPlatformImpl;
import me.towdium.jecharacters.uitest.platform.FabricInputPlatform;
import me.towdium.jecharacters.uitest.platform.FabricPlatform;
import me.towdium.jecharacters.uitest.platform.InputPlatform;
import me.towdium.jecharacters.uitest.platform.Platform;
import me.towdium.jecharacters.uitest.platform.ServerPlatform;
import me.towdium.jecharacters.uitest.platform.ServerPlatformImpl;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public final class UiTest implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Platform.register(new FabricPlatform());
        InputPlatform.register(new FabricInputPlatform());
        ClientPlatform.register(new ClientPlatformImpl());
        CapturePlatform.register(new CapturePlatformImpl());
        ScreenInput.register(new ScreenInputImpl());
        ServerPlatform.register(new ServerPlatformImpl());
        ClientCommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess) -> UiTestCommands.register(dispatcher));
    }
}
