package me.towdium.hecharacters.core;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import me.towdium.hecharacters.HechCommand;
import me.towdium.hecharacters.HechConfig;
import me.towdium.pinin.Keyboard;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class HechModContainer extends DummyModContainer {
    public HechModContainer() {
        super(new ModMetadata());
        ModMetadata meta = getMetadata();
        meta.modId = "hecharacters";
        meta.name = "Had Enough Characters";
        meta.version = "@VERSION@";
        meta.authorList = Arrays.asList("Towdium", "vfyjxf", "Rongmario");
        meta.description = "Help HEI read Pinyin";
        meta.url = "https://www.curseforge.com/minecraft/mc-mods/had-enough-characters";
        meta.logoFile = "icon.png";
    }

    @Override
    public List<ArtifactVersion> getDependencies() {
        return Collections.singletonList(VersionParser.parseVersionReference("jei@[4.22.0,)"));
    }

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        bus.register(this);
        return true;
    }

    @Override
    public File getSource() {
        return HechCore.source;
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
        ICommand c = new HechCommand();
        event.registerServerCommand(c);
    }

    @Subscribe
    public static void initPost(FMLPostInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(EventHandler.class);
    }

    @Override
    public String getGuiClassName() {
        return "me.towdium.hecharacters.HechGuiFactory";
    }

    public static class EventHandler {
        static boolean messageSent = false;

        @SubscribeEvent
        public static void onPlayerLogin(EntityJoinWorldEvent event) {
            if (event.getEntity() instanceof EntityPlayer && event.getEntity().world.isRemote
                    && HechConfig.enableChatHelp && !messageSent
                    && (HechConfig.keyboard == HechConfig.Spell.QUANPIN || !HechConfig.enableForceQuote)
                    && Minecraft.getMinecraft().gameSettings.language.equals("zh_tw")) {
                event.getEntity().sendMessage(new TextComponentTranslation("chat.taiwan"));
                messageSent = true;
            }
        }
    }
}
