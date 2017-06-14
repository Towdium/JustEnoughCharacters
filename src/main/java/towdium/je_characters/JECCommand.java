package towdium.je_characters;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Author: Towdium
 * Date:   14/06/17
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class JECCommand extends CommandBase {
    @Override
    public String getName() {
        return "jech";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "\njech profiler: Scans all your mods for 'String.contains' call.\n" +
                "    -s: Send result to author.";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 1 && args[0].equals("profiler")) {
            executeProfiler(server, sender);
        }
    }

    public void executeProfiler(MinecraftServer server, ICommandSender sender) {
        sender.sendMessage(new TextComponentString("Start profiling."));
    }
}
