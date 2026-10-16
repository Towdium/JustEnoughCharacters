package me.towdium.jecharacters.uitest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.towdium.jecharacters.uitest.engine.UITestRunner;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * {@code /jechtest list|run <selection>}, the manual entry point of the harness.
 *
 * <p>It runs scenarios against the world the player already has loaded and leaves the client up
 * afterwards, which is what makes a scenario debuggable by hand. The upstream harness hung this under
 * a shared {@code /cloudlib} root next to the library's own commands; this mod owns its whole
 * {@code /jechtest} root, so there is nothing to merge with.
 *
 * <p><b>Port note (NeoForge → Fabric).</b> The NeoForge copy takes the dispatcher from
 * {@code RegisterClientCommandsEvent}; on fabric-api's client command API the callback hands the same
 * {@link CommandDispatcher} over, so only the entry method differs.
 *
 * <p><b>Port note (MC 26.1.2).</b> fabric-api renamed the builder helper class
 * {@code ClientCommandManager} to {@code ClientCommands} — the {@code literal}/{@code argument}
 * statics are the same, so only the import moved.
 */
public final class UiTestCommands {

    private UiTestCommands() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(uiTest());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> uiTest() {
        return literal("jechtest").then(literal("list").executes(ctx -> {
            List<String> names = ScenarioRegistry.names();
            ctx.getSource()
                    .sendFeedback(Component.literal(names.size() + " scenario(s): " + String.join(", ", names)));
            return names.size();
        })).then(
            literal("run").then(
                argument("selection", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(selections(), builder))
                        .executes(ctx -> {
                            String selection = StringArgumentType.getString(ctx, "selection");
                            ctx.getSource()
                                    .sendFeedback(Component.literal(UITestRunner.runInteractive(selection)));
                            return 1;
                        })
            )
        );
    }

    /** The suggestion list: the {@code all} term plus every registered scenario name. */
    private static List<String> selections() {
        List<String> terms = new ArrayList<>();
        terms.add("all");
        terms.addAll(ScenarioRegistry.names());
        return terms;
    }
}
