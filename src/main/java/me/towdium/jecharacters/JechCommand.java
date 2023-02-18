package me.towdium.jecharacters;

import com.google.gson.GsonBuilder;
import mcp.MethodsReturnNonnullByDefault;
import me.towdium.jecharacters.util.Profiler;
import net.minecraft.client.resources.I18n;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.List;

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
        return "command.desc";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length == 1 && "profile".equals(args[0])) {
            Thread t = new Thread(() -> {
                sender.sendMessage(new TextComponentString(I18n.format("chat.start")));
                Profiler.Report r = Profiler.run();
                try (FileOutputStream fos = new FileOutputStream("logs/jecharacters-profiler.txt")) {
                    OutputStreamWriter osw = new OutputStreamWriter(fos);
                    osw.write(new GsonBuilder().setPrettyPrinting().create().toJson(r));
                    osw.flush();
                    sender.sendMessage(new TextComponentString(I18n.format("chat.saved")));
                } catch (IOException e) {
                    sender.sendMessage(new TextComponentString(I18n.format("chat.save_error")));
                }
            });
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        } else if (args.length == 2 && "verbose".equals(args[0])) {
            switch (args[1].toLowerCase()) {
                case "true":
                    JechConfig.enableVerbose = true;
                    break;
                case "false":
                    JechConfig.enableVerbose = false;
                    break;
                default:
                    sender.sendMessage(new TextComponentTranslation("command.unknown"));
                    break;
            }
        } else if (args.length == 2 && "keyboard".equals(args[0])) {
            switch (args[1].toLowerCase()) {
                case "quanpin":
                    JechConfig.setKeyboard(JechConfig.Spell.QUANPIN);
                    break;
                case "daqian":
                    JechConfig.setKeyboard(JechConfig.Spell.DAQIAN);
                    break;
                case "xiaohe":
                    JechConfig.setKeyboard(JechConfig.Spell.XIAOHE);
                    break;
                case "ziranma":
                    JechConfig.setKeyboard(JechConfig.Spell.ZIRANMA);
                    break;
                case "sougou":
                    JechConfig.setKeyboard(JechConfig.Spell.SOUGOU);
                    break;
                case "guobiao":
                    JechConfig.setKeyboard(JechConfig.Spell.GUOBIAO);
                    break;
                case "microsoft":
                    JechConfig.setKeyboard(JechConfig.Spell.MICROSOFT);
                    break;
                case "pinyinjiajia":
                    JechConfig.setKeyboard(JechConfig.Spell.PINYINPP);
                    break;
                case "ziguang":
                    JechConfig.setKeyboard(JechConfig.Spell.ZIGUANG);
                    break;
                default:
                    sender.sendMessage(new TextComponentTranslation("command.unknown"));
                    break;
            }
        } else {
            sender.sendMessage(new TextComponentTranslation("command.unknown"));
        }
    }

    @Override
    public List<String> getTabCompletions(
            MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1)
            return getListOfStringsMatchingLastWord(args, "profile", "verbose", "keyboard");
        else if (args.length == 2 && "verbose".equals(args[0]))
            return getListOfStringsMatchingLastWord(args, "true", "false");
        else if (args.length == 2 && "keyboard".equals(args[0]))
            return getListOfStringsMatchingLastWord(args, "quanpin", "daqian", "xiaohe", "ziranma", "sougou", "guobiao", "microsoft", "pinyinjiajia", "ziguang");
        else
            return Collections.emptyList();
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }
}
