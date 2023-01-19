package me.towdium.jecharacters.mixins.config;

import me.towdium.jecharacters.JustEnoughCharacters;
import me.towdium.jecharacters.mixins.utils.FeedFetcher;
import me.towdium.jecharacters.mixins.utils.MixinGenerator;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static me.towdium.jecharacters.JustEnoughCharacters.logger;

public class JechMixinPlugin implements IMixinConfigPlugin {

    public volatile static List<String> MIXIN_CLASSES = new ArrayList<>();

    @Override
    public void onLoad(String mixinPackage) {
        boolean success = true;

        logger.info("Jech is trying to download update data from network,it may take a while.");
        logger.info("Getting mixin data from server...");
        try {
            new FeedFetcher().fetch();
        } catch (IOException e) {
            logger.error("Failed to get mixin data from server, using local data instead.");
            success = false;
        }
        if (success) logger.info("Successfully got data from server.");

        logger.info("Generating mixin classes...");
        try (InputStream is = JustEnoughCharacters.class.getClassLoader().getResourceAsStream("mixins.ini")) {
            if (is != null) {
                List<String> original = IOUtils.readLines(is, StandardCharsets.UTF_8);
                for (String current : original) {
                    String[] pair = current.split(":", 2);
                    MIXIN_CLASSES.add(pair[0]);
                    if (pair.length > 1) removeRepetitive(pair[1]);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        MixinGenerator.generate();
        logger.info("Generating mixin classes finished!");

    }

    private void removeRepetitive(String target) {
        FeedFetcher.Feed.suffix.remove(target);
        FeedFetcher.Feed.contains.remove(target);
        FeedFetcher.Feed.regexp.remove(target);
        FeedFetcher.Feed.equals.remove(target);
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
