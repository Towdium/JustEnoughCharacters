package me.towdium.jecharacters.uitest;

import net.fabricmc.api.ClientModInitializer;

/**
 * The dev-only UI test harness, packaged as a mod of its own.
 *
 * <p>Nothing but a harness run loads it: the fabric harness run puts the harness source sets on the
 * run's class path (and only when {@code -PjechTest} was given), every other run and production do
 * not, and {@code jecharacters}' jar carries neither its classes nor its mixins. That is what keeps
 * the instrumentation out of a normal dev client and out of the shipped artifact while still letting
 * a booted client apply the input-capture mixins and start a run from the system properties.
 *
 * <p>It lives in the mod's entry source set rather than with the engine: {@code fabric.mod.json}
 * names this class as the mod's {@code client} entrypoint and names {@link SmokeScenario} under the
 * harness's own scenario entrypoint ({@code jechtest:scenarios}), so the engine stays free of both
 * definitions and the entry set depends on the engine rather than the other way round.
 *
 * <p><b>Port note (1.21.1 fabric → 1.16.5 fabric).</b> The 1.21.1 copy registered the harness's
 * interactive {@code /jechtest} root here, through fabric-api's client command API v2
 * ({@code ClientCommandRegistrationCallback}). That API generation does not exist in the 1.16.5
 * fabric-api this line pins — 0.42.0+1.16 predates client-command-api-v2 entirely — so this copy
 * carries no interactive command at all: the JSON engine path (a run selected by
 * {@code -PjechTest} and driven by the frame pump) is unaffected, and that is the only entry
 * point the jt pipeline uses.
 *
 * @see UiTestConstants#modId
 */
public final class UiTest implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // No command registration on this line; see the port note above.
    }
}
