package towdium.je_characters.core;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import towdium.je_characters.JECCommand;

import java.util.Collections;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class ModContainer extends DummyModContainer {
    public ModContainer() {
        super(new ModMetadata());
        ModMetadata meta = getMetadata();
        meta.modId = "je_characters";
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

    @Subscribe
    public void onServerStart(FMLServerStartingEvent event) {
        event.registerServerCommand(new JECCommand());
    }
}
