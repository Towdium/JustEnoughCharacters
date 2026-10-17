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

public final class UiTestCommands {

    private UiTestCommands() {}

    public static void register(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.isProduction()) return;
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

    private static List<String> selections() {
        List<String> terms = new ArrayList<>();
        terms.add("all");
        terms.addAll(ScenarioRegistry.names());
        return terms;
    }
}
