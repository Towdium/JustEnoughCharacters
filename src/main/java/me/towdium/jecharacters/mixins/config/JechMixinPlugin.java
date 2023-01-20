package me.towdium.jecharacters.mixins.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.towdium.jecharacters.JustEnoughCharacters;
import me.towdium.jecharacters.mixins.utils.FeedFetcher;
import me.towdium.jecharacters.mixins.utils.MixinGenerator;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.zip.ZipFile;

import static me.towdium.jecharacters.JustEnoughCharacters.logger;

public class JechMixinPlugin implements IMixinConfigPlugin {

    public volatile static List<String> MIXIN_CLASSES = new ArrayList<>();

    @Override
    public void onLoad(String mixinPackage) {
        Path jarPath = FabricLoader.getInstance()
                .getGameDir()
                .resolve("mods")
                .resolve("jecharacters-mixins.jar");
        File jarFile = jarPath.toFile();
        //Loading cached data
        if (jarFile.exists()) {
            try (ZipFile zipFile = new ZipFile(jarFile)) {
                zipFile.stream().forEach(file -> {
                    if (file.getName().endsWith(".class")) {
                        String className = file.getName();
                        className = className.substring(className.indexOf("update"), className.length() - 6)
                                .replace("/", ".");
                        MIXIN_CLASSES.add(className);
                    }
                });
            } catch (IOException e) {
                logger.error("Failed to load mixin classes from jar file", e);
            }
            FabricLauncherBase.getLauncher().addToClassPath(jarPath);
        }
        //Loading internal mixin data
        List<String> targets = new ArrayList<>();
        try (InputStream is = JustEnoughCharacters.class.getClassLoader().getResourceAsStream("mixins.ini")) {
            if (is != null) {
                List<String> original = IOUtils.readLines(is, StandardCharsets.UTF_8);
                for (String current : original) {
                    String[] pair = current.split(":", 2);
                    MIXIN_CLASSES.add(pair[0]);
                    //The manual mixin does not have a target
                    if (pair.length > 1) targets.add(pair[1]);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //Load classes in advance to prevent deadlocks caused by loading classes in network threads.
        try {
            //noinspection unused
            JsonObject unused = JsonParser.parseString("{\"fake\": {\"array\": [\"empty\"], \"empty\":{}}}").getAsJsonObject();
            FeedFetcher.INSTANCE.fetch(true);
            MixinGenerator.generate(null);
        } catch (Exception exception) {
            //Do nothing.
        }
        //Loading remote mixin data
        CompletableFuture.supplyAsync(() -> {
                    logger.info("Jech is trying to download update data from network,it may take a while.");
                    logger.info("Getting mixin data from server...");
                    return FeedFetcher.INSTANCE.fetch(false);
                }, Executors.newSingleThreadExecutor())
                .exceptionally(e -> {
                    logger.error("Failed to get mixin data from server, using local data instead.", e);
                    return null;
                })
                .thenAccept(success -> {
                    if (success) {
                        logger.info("Successfully got data from server.");
                        logger.info("Generating mixin classes...");
                        for (String target : targets) removeRepetitive(target);
                        MixinGenerator.generate(jarPath);
                        logger.info("Generating mixin classes finished!");
                    }
                }).exceptionally( e -> {
                    logger.error("Failed to generate mixin classes.", e);
                    return null;
                });
    }

    private void removeRepetitive(String target) {
        FeedFetcher.suffix.remove(target);
        FeedFetcher.contains.remove(target);
        FeedFetcher.regexp.remove(target);
        FeedFetcher.equals.remove(target);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return MIXIN_CLASSES;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
