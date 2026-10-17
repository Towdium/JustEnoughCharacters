package me.towdium.jecharacters.uitest;

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
            MinecraftForge.EVENT_BUS.addListener(UiTestCommands::register);
        }
    }
}
