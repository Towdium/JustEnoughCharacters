package me.towdium.jecharacters.mixin;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.towdium.jecharacters.asm.ITransformer;
import me.towdium.jecharacters.asm.JechClassTransformer;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.transformers.TreeTransformer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Based on SpASM
 */
public class JechMixinPlugin implements IMixinConfigPlugin {

    static final ImmutableList<ITransformer> TRANSFORMERS = entrypoint("transformer", ITransformer.class);

    public JechMixinPlugin() {

    }

    static {
        try {
            hook();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static <ENTRYPOINT> ImmutableList<ENTRYPOINT> entrypoint(final @NotNull String key, final @NotNull Class<ENTRYPOINT> entrypointClass) {
        return ImmutableList.copyOf(FabricLoader.getInstance().getEntrypoints("jech:" + key, entrypointClass));
    }

    @SuppressWarnings("unchecked")
    private static <T extends TreeTransformer & IMixinTransformer> void hook() throws NoSuchFieldException, IllegalAccessException {
        ClassLoader knotClassLoader = JechMixinPlugin.class.getClassLoader();
        Field knotClassDelegateField = knotClassLoader.getClass().getDeclaredField("delegate");
        knotClassDelegateField.setAccessible(true);
        Object knotClassDelegate = knotClassDelegateField.get(knotClassLoader);
        Field mixinTransformerField = knotClassDelegate.getClass().getDeclaredField("mixinTransformer");
        mixinTransformerField.setAccessible(true);
        InputStream is = JechClassTransformer.class.getClassLoader().getResourceAsStream("me/towdium/jecharacters/targets.json");
        if (is == null) {
            throw new RuntimeException("Could not find targets.json. JechTransformer will not be loaded.");
        }
        JsonObject targets = new JsonParser().parse(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();

        mixinTransformerField.set(knotClassDelegate, new MixinTransformerHook<>((T) mixinTransformerField.get(knotClassDelegate), new JechClassTransformer(TRANSFORMERS, targets)));
    }

    @Override
    public void onLoad(String mixinPackage) {
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
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
