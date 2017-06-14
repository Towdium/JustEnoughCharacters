package towdium.je_characters;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.resources.I18n;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import towdium.je_characters.util.MailSender;
import towdium.je_characters.util.Profiler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 * Author: Towdium
 * Date:   14/06/17
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class JechCommand extends CommandBase {
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
        if (args.length >= 1 && args[0].equals("profiler")) {
            executeProfiler(server, sender, args);
        }
    }

    public void executeProfiler(MinecraftServer server, ICommandSender sender, String[] args) {
        sender.sendMessage(new TextComponentString(I18n.format("chat.start")));
        Thread t = new Thread(() -> {
            String s = Profiler.run();
            try (FileOutputStream fos = new FileOutputStream("logs/je_characters-profiler.txt")) {
                OutputStreamWriter osw = new OutputStreamWriter(fos);
                osw.write(s);
                osw.flush();
                sender.sendMessage(new TextComponentString(I18n.format("chat.saved")));
            } catch (IOException e) {
                sender.sendMessage(new TextComponentString(I18n.format("chat.saveError")));
            }

            if (args.length == 2 && args[1].equals("-s")) {
                try {
                    MailSender.send("Report", s);
                    sender.sendMessage(new TextComponentString(I18n.format("chat.sent")));
                } catch (IOException e) {
                    sender.sendMessage(new TextComponentString(I18n.format("chat.sendError")));
                }
            } else {
                sender.sendMessage(new TextComponentString(I18n.format("chat.sendNot")));
            }
        });
        t.setPriority(Thread.MIN_PRIORITY);
        t.run();
    }
}
