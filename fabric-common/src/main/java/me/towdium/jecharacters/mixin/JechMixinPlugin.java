package me.towdium.jecharacters.mixin;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.towdium.jecharacters.asm.Transformer;
import me.towdium.jecharacters.asm.JechClassTransformer;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.transformers.TreeTransformer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Based on SpASM
 */
public class JechMixinPlugin implements IMixinConfigPlugin {

    static final ImmutableList<Transformer> TRANSFORMERS = entrypoint("transformer", Transformer.class);
    private static final Logger log = LoggerFactory.getLogger(JechMixinPlugin.class);

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
        JsonObject targets = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();

        File configDataFile = FabricLoader.getInstance().getConfigDir().resolve("jecharacters-extra.json").toFile();
        if (!configDataFile.exists()) {
            JsonObject template = new JsonObject();
            template.add("removals", new JsonArray());
            template.add("contains", withDefaults());
            template.add("equals", withDefaults());
            template.add("suffix", withDefaults());
            template.add("regExp", withDefaults());
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(template);
            configDataFile.getParentFile().mkdirs();
            try {
                configDataFile.createNewFile();
            } catch (IOException e) {
                log.error("Could not create config file", e);
            }
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(configDataFile), StandardCharsets.UTF_8)) {
                writer.write(json);
                writer.flush();
            } catch (IOException e) {
                log.error("Could not write to config file", e);
            }
        } else {
            try {
                String s = Files.readString(configDataFile.toPath());
                JsonObject extra = JsonParser.parseString(s).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : extra.entrySet()) {
                    if (entry.getKey().equals("removals")) {
                        entry.getValue().getAsJsonArray()
                                .forEach(targets.get("removals").getAsJsonArray()::add);
                    } else {
                        JsonObject value = entry.getValue().getAsJsonObject();
                        value.get("additional").getAsJsonArray()
                                .forEach(target -> targets.get(entry.getKey()).getAsJsonObject().get("additional").getAsJsonArray().add(target));
                    }
                }
            } catch (IOException e) {
                log.error("Could not read config file", e);
            }
        }

        mixinTransformerField.set(knotClassDelegate, new MixinTransformerHook<>((T) mixinTransformerField.get(knotClassDelegate), new JechClassTransformer(TRANSFORMERS, targets)));
    }

    private static JsonObject withDefaults() {
        JsonObject template = new JsonObject();
        template.add("additional", new JsonArray());
        return template;
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
