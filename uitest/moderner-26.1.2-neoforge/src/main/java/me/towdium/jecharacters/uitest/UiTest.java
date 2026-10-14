package me.towdium.jecharacters.uitest;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The dev-only UI test harness, packaged as a mod of its own.
 *
 * <p>Nothing but the harness run loads it: {@code clientTests} lists it among its loaded mods, every
 * other run and production do not, and {@code jecharacters}' jar carries neither its classes nor its
 * mixins. That is what keeps the instrumentation out of a normal dev client and out of the shipped
 * artifact while still letting a booted client register {@code /jechtest} and apply the
 * input-capture mixins.
 *
 * <p>It lives in the mod's entry source set rather than with the engine, mirroring the upstream
 * CloudLib harness: FML rejects a mod file that contains a {@code @Mod} class whose id the file's
 * {@code neoforge.mods.toml} does not declare, so the engine stays free of this class and the entry
 * set depends on the engine rather than the other way round.
 *
 * <p>The command is registered on the game bus only on the client.
 *
 * @see UiTestConstants#modId
 * @see UiTestCommands
 */
@Mod(UiTestConstants.modId)
public final class UiTest {

    public UiTest(Dist dist) {
        if (dist.isClient()) {
            NeoForge.EVENT_BUS.addListener(UiTestCommands::register);
        }
    }
}
