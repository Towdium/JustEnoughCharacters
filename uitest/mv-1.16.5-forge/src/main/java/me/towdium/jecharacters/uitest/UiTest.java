package me.towdium.jecharacters.uitest;

import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The dev-only UI test harness, packaged as a mod of its own.
 *
 * <p>Nothing but the harness run loads it: the harness build's own run lists it among its loaded
 * mods, every other run and production do not, and {@code jecharacters}' jar carries neither its
 * classes nor its mixins. That is what keeps the instrumentation out of a normal dev client and out
 * of the shipped artifact while still letting a booted client apply the input-capture mixins and
 * drive its scenarios from the harness run's own frame pump.
 *
 * <p><b>Port note (Forge 1.20.1 → Forge 1.16.5).</b> The {@code /jechtest} manual chat command
 * ({@code UiTestCommands} in the newer copies) does not travel to this line: it hung on
 * {@code RegisterClientCommandsEvent}, a client-command registration event Forge first carries in
 * 1.19 — the 1.16.5-era answer is third-party hooks a dev-only harness has no reason to adopt.
 * The JSON scenario path (the one jt drives) needs no command, so the entry point keeps only a
 * load marker that makes a misconfigured load (a plain client, a server) distinguishable in the
 * log.
 *
 * @see UiTestConstants#modId
 */
@net.minecraftforge.fml.common.Mod(UiTestConstants.modId)
public final class UiTest {

    private static final Logger log = LoggerFactory.getLogger("jechtest-uitest");

    public UiTest() {
        if (FMLEnvironment.dist.isClient()) {
            log.info("jechtest harness loaded on the client");
        }
    }
}
