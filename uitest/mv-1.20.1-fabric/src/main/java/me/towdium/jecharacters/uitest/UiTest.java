package me.towdium.jecharacters.uitest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

/**
 * The dev-only UI test harness, packaged as a mod of its own.
 *
 * <p>Nothing but a harness run loads it: the fabric harness run puts the harness source sets on the
 * run's class path (and only when {@code -PjechTest} was given), every other run and production do
 * not, and {@code jecharacters}' jar carries neither its classes nor its mixins. That is what keeps
 * the instrumentation out of a normal dev client and out of the shipped artifact while still letting
 * a booted client register {@code /jechtest} and apply the input-capture mixins.
 *
 * <p>It lives in the mod's entry source set rather than with the engine: {@code fabric.mod.json}
 * names this class as the mod's {@code client} entrypoint and names {@link SmokeScenario} under the
 * harness's own scenario entrypoint ({@code jechtest:scenarios}), so the engine stays free of both
 * definitions and the entry set depends on the engine rather than the other way round.
 *
 * <p><b>Port note (NeoForge → Fabric).</b> The NeoForge copy is a {@code @Mod(UiTestConstants.modId)}
 * class whose constructor hangs {@link UiTestCommands#register} on the game event bus. Here the mod is
 * a {@link ClientModInitializer}, and the command root is registered through fabric-api's
 * {@link ClientCommandRegistrationCallback}; the command tree itself is the same code.
 *
 * @see UiTestConstants#modId
 * @see UiTestCommands
 */
public final class UiTest implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess) -> UiTestCommands.register(dispatcher));
    }
}
