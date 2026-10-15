package me.towdium.jecharacters.uitest;

import me.towdium.jecharacters.uitest.engine.UITestRunner;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code /jechtest list|run <selection>}, the manual entry point of the harness.
 *
 * <p><b>Port note.</b> 1.21.1 registers through Brigadier on
 * {@code RegisterClientCommandsEvent}; 1.12.2 has no Brigadier, and the client-side command root is
 * {@code net.minecraftforge.client.ClientCommandHandler}, registered once at init. The verb set and
 * the return shape (list the registered scenarios, run a selection against the world the player
 * already has loaded) are the same.
 */
public final class UiTestCommands extends CommandBase {

    @Override
    public String getName() {
        return "jechtest";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/jechtest list | run <selection>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length >= 1 && "list".equals(args[0])) {
            List<String> names = ScenarioRegistry.names();
            sender.sendMessage(new TextComponentString(names.size() + " scenario(s): " + String.join(", ", names)));
            return;
        }
        if (args.length >= 2 && "run".equals(args[0])) {
            StringBuilder selection = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (selection.length() > 0) selection.append(' ');
                selection.append(args[i]);
            }
            sender.sendMessage(new TextComponentString(UITestRunner.runInteractive(selection.toString())));
            return;
        }
        sender.sendMessage(new TextComponentString(getUsage(sender)));
    }

    @Override
    public List<String> getTabCompletions(
            MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "list", "run");
        if (args.length == 2 && "run".equals(args[0])) {
            List<String> terms = new ArrayList<String>();
            terms.add("all");
            terms.addAll(ScenarioRegistry.names());
            return getListOfStringsMatchingLastWord(args, terms.toArray(new String[0]));
        }
        return Collections.emptyList();
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }
}
