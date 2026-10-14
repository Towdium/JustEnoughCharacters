package me.towdium.jecharacters.uitest;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.towdium.jecharacters.uitest.engine.UITestRunner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /jechtest list|run <selection>}, the manual entry point of the harness.
 *
 * <p>It runs scenarios against the world the player already has loaded and leaves the client up
 * afterwards, which is what makes a scenario debuggable by hand. The upstream harness hung this under
 * a shared {@code /cloudlib} root next to the library's own commands; this mod owns its whole
 * {@code /jechtest} root, so there is nothing to merge with.
 */
public final class UiTestCommands {

    private UiTestCommands() {}

    public static void register(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(uiTest());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> uiTest() {
        return literal("jechtest").then(literal("list").executes(ctx -> {
            List<String> names = ScenarioRegistry.names();
            ctx.getSource()
                    .sendSystemMessage(Component.literal(names.size() + " scenario(s): " + String.join(", ", names)));
            return names.size();
        })).then(
            literal("run").then(
                argument("selection", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(selections(), builder))
                        .executes(ctx -> {
                            String selection = StringArgumentType.getString(ctx, "selection");
                            ctx.getSource()
                                    .sendSystemMessage(Component.literal(UITestRunner.runInteractive(selection)));
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
