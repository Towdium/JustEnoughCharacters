package me.towdium.jecharacters.uitest;

import me.towdium.jecharacters.uitest.engine.UITestRunner;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * The dev-only UI test harness, packaged as a mod of its own.
 *
 * <p>Nothing but the harness run loads it: {@code clientTests} lists it among its loaded mods, every
 * other run and production do not, and {@code jecharacters}' jar carries neither its classes nor its
 * coremod. That is what keeps the instrumentation out of a normal dev client and out of the shipped
 * artifact while still letting a booted client register {@code /jechtest} and the input seam.
 *
 * <p>It lives in the mod's entry source set rather than with the engine, mirroring the other
 * checkouts: the engine stays free of the {@code @Mod} class and the entry set depends on the engine
 * rather than the other way round.
 *
 * <p><b>Port note.</b> {@code clientSideOnly = true} is 1.12.2's spelling of "the harness never
 * exists on a dedicated server", and it is what lets this class name client-only FML types
 * ({@code ClientCommandHandler}) directly. The one thing pre-init must do is hand FML's annotation
 * table to the scenario registry, because FML exposes it only on the event.
 */
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
