package me.towdium.jecharacters.uitest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.towdium.jecharacters.uitest.engine.UITestRunner;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class UiTestCommands {

    private UiTestCommands() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
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

    private static List<String> selections() {
        List<String> terms = new ArrayList<>();
        terms.add("all");
        terms.addAll(ScenarioRegistry.names());
        return terms;
    }
}
