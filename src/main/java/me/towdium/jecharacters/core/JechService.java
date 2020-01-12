package me.towdium.jecharacters.core;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import mcp.MethodsReturnNonnullByDefault;
import me.towdium.jecharacters.core.transformers.TJeiQuote;
import me.towdium.jecharacters.core.transformers.TJeiTree;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class JechService implements ITransformationService {
    private static String location;

    @Override
    public Map.Entry<Set<String>, Supplier<Function<String, Optional<URL>>>> additionalClassesLocator() {
        Set<String> path = new HashSet<>();
        path.add("me.towdium.jecharacters.safe.");
        path.add("me.towdium.pinin.");
        return new AbstractMap.SimpleEntry<>(path, () -> this::locate);
    }

    @Override
    public Map.Entry<Set<String>, Supplier<Function<String, Optional<URL>>>> additionalResourcesLocator() {
        Set<String> path = new HashSet<>();
        path.add("me.towdium.class");
        return new AbstractMap.SimpleEntry<>(path, () -> this::locate);
    }

    private Optional<URL> locate(String name) {
        try {
            URL urlJar = new URL("jar:" + location + "!/" + name);
            return Optional.of(urlJar);
        } catch (IOException var5) {
            return Optional.empty();
        }
    }

    @Override
    public String name() {
        return "jecharacters";
    }

    @Override
    public void initialize(IEnvironment environment) {
        location = JechService.class.getProtectionDomain()
                .getCodeSource().getLocation().toExternalForm();
    }

    @Override
    public void beginScanning(IEnvironment environment) {
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List<ITransformer> transformers() {
        return Arrays.asList(new TJeiTree(), new TJeiQuote());
    }
}
