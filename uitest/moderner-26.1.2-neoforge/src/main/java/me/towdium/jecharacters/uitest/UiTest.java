package me.towdium.jecharacters.uitest;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(UiTestConstants.modId)
public final class UiTest {

    public UiTest(Dist dist) {
        if (dist.isClient()) {
            NeoForge.EVENT_BUS.addListener(UiTestCommands::register);
        }
    }
}
