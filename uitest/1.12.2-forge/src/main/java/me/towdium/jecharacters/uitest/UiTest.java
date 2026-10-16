package me.towdium.jecharacters.uitest;

import me.towdium.jecharacters.uitest.engine.UITestRunner;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(
        modid = UiTestConstants.modId,
        name = "JECh UI Test Harness",
        version = UiTestConstants.version,
        clientSideOnly = true,
        dependencies = "required-after:jecharacters")
public final class UiTest {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ScenarioRegistry.setTable(event.getAsmData());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new UiTestCommands());
        UITestRunner.bootstrap();
    }
}
