package me.towdium.jecharacters.uitest.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Map;

/**
 * The dev-only UI harness's coremod: this line's replacement for the eleven Mixins the 1.21.1
 * harness ships.
 *
 * <p>It is loaded the same way {@code me.towdium.jecharacters.core.JechCore} is — an
 * {@link IFMLLoadingPlugin} whose class is registered with FML before any game class loads — except
 * that it travels on the command line rather than in a jar manifest: the harness run passes
 * {@code -Dfml.coreMods.load=me.towdium.jecharacters.uitest.core.UiTestCore} (CoreModManager,
 * "Found a command line coremod"), because the harness is a mod root and not a jar. That is the
 * only place it is ever registered, so a plain {@code runClient} never loads it and pays nothing.
 *
 * <p>{@link IFMLLoadingPlugin.TransformerExclusions} keeps the harness's own package out of its own
 * transformer, which would otherwise rewrite the seam classes it depends on.
 */
@IFMLLoadingPlugin.Name("jechtest")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions({"me.towdium.jecharacters.uitest."})
public class UiTestCore implements IFMLLoadingPlugin {

    /** The harness's own logger, the same shape {@code JechCore.LOG} has. */
    public static final Logger LOG = LogManager.getLogger("jechtest");

    /** Where the client's game directory is, filled in by FML. */
    public static File gameDir;

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{"me.towdium.jecharacters.uitest.core.UiTestTransformer"};
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        Object location = data.get("mcLocation");
        if (location instanceof File) gameDir = (File) location;
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
