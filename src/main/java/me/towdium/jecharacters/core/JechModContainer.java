package me.towdium.jecharacters.core;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import me.towdium.jecharacters.JechCommand;
import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.JechGuiFactory;
import me.towdium.jecharacters.match.Keyboard;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import net.minecraftforge.fml.common.versioning.VersionParser;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class JechModContainer extends DummyModContainer {
    public JechModContainer() {
        super(new ModMetadata());
        ModMetadata meta = getMetadata();
        meta.modId = "jecharacters";
        meta.name = "Just Enough Characters";
        meta.version = "@VERSION@";
        meta.authorList = Collections.singletonList("Towdium");
        meta.description = "Help JEI read PinyinPattern";
        meta.url = "https://minecraft.curseforge.com/projects/just-enough-characters";
    }

    @Override
    public List<ArtifactVersion> getDependencies() {
        return Collections.singletonList(VersionParser.parseVersionReference("jei@[4.9.2,)"));
    }

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        bus.register(this);
        return true;
    }

    @Override
    public File getSource() {
        return JechCore.source;
    }

    @Override
    public Class<?> getCustomResourcePackClass() {
        try {
            return getSource().isDirectory() ?
                    Class.forName("net.minecraftforge.fml.client.FMLFolderResourcePack",
                            true, getClass().getClassLoader()) :
                    Class.forName("net.minecraftforge.fml.client.FMLFileResourcePack",
                            true, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }


    @Subscribe
    public void onServerStart(FMLServerStartingEvent event) {
        ICommand c = new JechCommand();
        event.registerServerCommand(c);
    }

    @Subscribe
    public static void initPost(FMLPostInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(JechGuiFactory.ConfigHandler.class);
        MinecraftForge.EVENT_BUS.register(EventHandler.class);
    }

    @Override
    public String getGuiClassName() {
        return "me.towdium.jecharacters.JechGuiFactory";
    }

    public static class EventHandler {
        static boolean messageSent = false;

        @SubscribeEvent
        public static void onPlayerLogin(EntityJoinWorldEvent event) {
            if (event.getEntity() instanceof EntityPlayer && event.getEntity().world.isRemote
                    && JechConfig.enableChatHelp && !messageSent
                    && (JechConfig.keyboard == Keyboard.QUANPIN || !JechConfig.enableForceQuote)
                    && Minecraft.getMinecraft().gameSettings.language.equals("zh_tw")) {
                event.getEntity().sendMessage(new TextComponentTranslation("chat.taiwan"));
                messageSent = true;
            }
        }
    }
}
