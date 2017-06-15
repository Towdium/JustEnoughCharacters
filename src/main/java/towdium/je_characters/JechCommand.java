package towdium.je_characters;

import com.google.gson.GsonBuilder;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.resources.I18n;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import towdium.je_characters.util.MailSender;
import towdium.je_characters.util.Profiler;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: Towdium
 * Date:   14/06/17
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class JechCommand extends CommandBase {
    private static List<String> parseArg(String[] args) {
        boolean quote = false;
        ArrayList<String> ret = new ArrayList<>();
        ArrayList<String> buffer = new ArrayList<>();
        for (String s : args) {
            if (!quote && s.startsWith("\"")) {
                if (s.endsWith("\"")) {
                    ret.add(s.substring(1, s.length() - 1));
                } else {
                    quote = true;
                    buffer.add(s.substring(1));
                }
                continue;
            } else if (quote && s.endsWith(("\""))) {
                quote = false;
                buffer.add(s.substring(0, s.length() - 1));
                ret.add(String.join(" ", buffer));
                continue;
            } else if (s.contains("\"")) {
                throw new RuntimeException("Illegal format.");
            }

            if (quote) {
                buffer.add(s);
            } else {
                ret.add(s);
            }
        }
        if (quote) {
            throw new RuntimeException("Illegal format.");
        } else {
            return ret;
        }
    }

    private static void executeProfiler(MinecraftServer server, ICommandSender sender, List<String> args) {
        if (args.size() == 1) {
            executeProfiler(false, null, sender);
        } else if (args.size() == 2 && args.get(1).equals("-s")) {
            executeProfiler(true, null, sender);
        } else if (args.size() == 3 && args.get(1).equals("-s")) {
            executeProfiler(true, args.get(2), sender);
        } else {
            sender.sendMessage(new TextComponentTranslation("command.syntaxError"));
        }
    }

    private static void executeProfiler(boolean send, @Nullable String comment, ICommandSender sender) {
        Thread t = new Thread(() -> {
            Profiler.Report r = Profiler.run();
            try (FileOutputStream fos = new FileOutputStream("logs/je_characters-profiler.txt")) {
                OutputStreamWriter osw = new OutputStreamWriter(fos);
                osw.write(new GsonBuilder().setPrettyPrinting().create().toJson(r));
                osw.flush();
                sender.sendMessage(new TextComponentString(I18n.format("chat.saved")));
            } catch (IOException e) {
                sender.sendMessage(new TextComponentString(I18n.format("chat.saveError")));
            }
            if (send) {
                try {
                    Message m = new Message();
                    m.content = r;
                    m.version = 1;
                    m.comment = comment;
                    MailSender.send("Report", new GsonBuilder().setPrettyPrinting().create().toJson(m));
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

    @Override
    public String getName() {
        return "jech";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "command.profiler.desc";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        List<String> arg;
        try {
            arg = parseArg(args);
        } catch (Exception e) {
            sender.sendMessage(new TextComponentTranslation("command.syntaxError"));
            return;
        }
        if (arg.size() >= 1 && args[0].equals("profiler")) {
            executeProfiler(server, sender, arg);
        } else {
            sender.sendMessage(new TextComponentTranslation("command.unknown"));
        }
    }

    private static class Message {
        String comment;
        int version = 1;
        Profiler.Report content;
    }
}
