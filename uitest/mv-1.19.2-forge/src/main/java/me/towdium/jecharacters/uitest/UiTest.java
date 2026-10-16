package me.towdium.jecharacters.uitest;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * The dev-only UI test harness, packaged as a mod of its own.
 *
 * <p>Nothing but the harness run loads it: the harness build's own run lists it among its loaded
 * mods, every other run and production do not, and {@code jecharacters}' jar carries neither its
 * classes nor its mixins. That is what keeps the instrumentation out of a normal dev client and out
 * of the shipped artifact while still letting a booted client register {@code /jechtest} and apply
 * the input-capture mixins.
 *
 * <p><b>Port note (NeoForge 1.21.1 → Forge 1.20.1).</b> Forge's {@code @Mod} constructors take no
 * {@code Dist} parameter (that injection arrived with NeoForge), so the client check reads
 * {@code FMLEnvironment.dist} instead; the command registration itself moves to the Forge event bus,
 * whose {@code RegisterClientCommandsEvent} this generation carries under the same name.
 *
 * @see UiTestConstants#modId
 * @see UiTestCommands
 */
@net.minecraftforge.fml.common.Mod(UiTestConstants.modId)
public final class UiTest {

    public UiTest() {
        if (FMLEnvironment.dist.isClient()) {
            MinecraftForge.EVENT_BUS.addListener(UiTestCommands::register);
        }
    }
}
