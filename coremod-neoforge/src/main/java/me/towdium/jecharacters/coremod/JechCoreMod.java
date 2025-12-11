package me.towdium.jecharacters.coremod;

import com.google.auto.service.AutoService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.towdium.jecharacters.asm.JechClassTransformer;
import me.towdium.jecharacters.asm.Transformer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.Set;
import java.util.stream.Collectors;

@AutoService(ClassProcessor.class)
public class JechCoreMod implements ClassProcessor {

    private final static Logger log = LogManager.getLogger("JechTransformer");
    private final JechClassTransformer transformer;
    private final Set<String> targets;

    public JechCoreMod() {
        InputStream is = JechClassTransformer.class.getClassLoader().getResourceAsStream("me/towdium/jecharacters/targets.json");
        if (is == null) {
            throw new RuntimeException("Could not find targets.json. JechTransformer will not be loaded.");
        }
        JsonObject targets = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
        var transformers = ServiceLoader.load(Transformer.class, JechCoreMod.class.getClassLoader())
                                        .stream()
                                        .map(Provider::get)
                                        .toList();
        File configDataFile = FMLPaths.CONFIGDIR.get().resolve("jecharacters-extra.json").toFile();
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
        transformer = new JechClassTransformer(transformers, targets);
        this.targets = transformer.getTransformers()
                                  .stream()
                                  .map(Transformer::targetClasses)
                                  .flatMap(Set::stream)
                                  .map(name -> name.replace('/', '.'))
                                  .collect(Collectors.toSet());
    }

    private static JsonObject withDefaults() {
        JsonObject template = new JsonObject();
        template.add("additional", new JsonArray());
        return template;
    }

    @Override
    public ProcessorName name() {
        return new ProcessorName("jecharacters", "coremod");
    }

    @Override
    public boolean handlesClass(SelectionContext context) {
        if (context.empty()) return false;
        return targets.contains(context.type().getClassName());
    }

    @Override
    public ComputeFlags processClass(TransformationContext context) {
        if (transformer.transform(context.node())) {
            context.audit("jecharacters", "applied");
            return ComputeFlags.COMPUTE_FRAMES;
        }
        return ComputeFlags.NO_REWRITE;
    }
}
