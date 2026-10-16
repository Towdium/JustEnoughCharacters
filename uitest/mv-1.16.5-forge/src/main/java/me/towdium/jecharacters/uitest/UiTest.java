package me.towdium.jecharacters.uitest;

import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@net.minecraftforge.fml.common.Mod(UiTestConstants.modId)
public final class UiTest {

    private static final Logger log = LoggerFactory.getLogger("jechtest-uitest");

    public UiTest() {
        if (FMLEnvironment.dist.isClient()) {
            log.info("jechtest harness loaded on the client");
        }
    }
}
