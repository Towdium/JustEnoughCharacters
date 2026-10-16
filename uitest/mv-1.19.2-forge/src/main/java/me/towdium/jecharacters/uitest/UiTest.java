package me.towdium.jecharacters.uitest;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLEnvironment;

@net.minecraftforge.fml.common.Mod(UiTestConstants.modId)
public final class UiTest {

    public UiTest() {
        if (FMLEnvironment.dist.isClient()) {
            MinecraftForge.EVENT_BUS.addListener(UiTestCommands::register);
        }
    }
}
