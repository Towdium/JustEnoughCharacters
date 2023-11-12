package me.towdium.jecharacters.coremod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import me.towdium.jecharacters.asm.JechClassTransformer;
import net.minecraftforge.fml.loading.ModDirTransformerDiscoverer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class JechServiceProvider implements ITransformationService {

    protected static final Logger LOGGER = LogManager.getLogger("JechService");

    @Override
    public @NotNull String name() {
        return "JechTransformService";
    }

    @Override
    public void initialize(@NotNull IEnvironment environment) {
        LOGGER.info("JechTransformService is initializing.");
        try {
            ModDirTransformerDiscoverer.getExtraLocators()
                    .add(
                            Paths.get(
                                    JechServiceProvider.class
                                            .getProtectionDomain()
                                            .getCodeSource()
                                            .getLocation()
                                            .toURI()));
        } catch (URISyntaxException e) {
            LOGGER.error(
                    "An unexpected issue occurred while injecting the custom ModLocator into Forge.", e);
        }
    }

    @Override
    public void beginScanning(@NotNull IEnvironment environment) {
    }

    @Override
    public void onLoad(@NotNull IEnvironment env, @NotNull Set<String> otherServices) {
    }

    @SuppressWarnings("rawtypes")
    @Override
    public @NotNull List<ITransformer> transformers() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        List<me.towdium.jecharacters.asm.ITransformer> transformers = StreamSupport.stream(ServiceLoader.load(me.towdium.jecharacters.asm.ITransformer.class, this.getClass().getClassLoader()).spliterator(), false)
                .collect(Collectors.toList());
        InputStream is = getClass().getClassLoader().getResourceAsStream("me/towdium/jecharacters/targets.json");
        if (is == null) {
            LOGGER.error("Could not find targets.json. JechTransformer will not be loaded.");
            return Collections.emptyList();
        }
        JsonObject targets = new JsonParser().parse(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();

        return Collections.singletonList(new ClassTransformer(new JechClassTransformer(transformers, targets)));
    }
}
