package me.towdium.jecharacters.core;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import me.towdium.jecharacters.JechCommand;
import net.minecraft.command.ICommand;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import java.io.File;
import java.util.Collections;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class ModContainer extends DummyModContainer {
    public ModContainer() {
        super(new ModMetadata());
        ModMetadata meta = getMetadata();
        meta.modId = "jecharacters";
        meta.name = "Just Enough Characters";
        meta.version = "@VERSION@";
        meta.authorList = Collections.singletonList("Towdium");
        meta.description = "Help JEI read Pinyin";
        meta.url = "https://minecraft.curseforge.com/projects/just-enough-characters";
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
            return getSource().isDirectory() ? Class.forName("net.minecraftforge.fml.client.FMLFolderResourcePack", true, getClass().getClassLoader()) : Class.forName("net.minecraftforge.fml.client.FMLFileResourcePack", true, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Subscribe
    public void onServerStart(FMLServerStartingEvent event) {
        ICommand c = new JechCommand();
        event.registerServerCommand(c);
    }
}
