package me.towdium.jecharacters.asm;

import com.google.gson.JsonObject;
import me.towdium.jecharacters.annotations.ParametersAreNonnullByDefault;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TODO:Use REGEXP to match lambda method names.
 */
@ParametersAreNonnullByDefault
public class JechClassTransformer {

    public static final Logger LOGGER = LogManager.getLogger("Jech Transformer");

    /**
     * Set by different environments.
     * <p>
     * Must be set before any transformation.
     * <p>
     * Default: Forge 1.20.1
     */
    public static String suffixClassName = "net/minecraft/client/searchtree/SuffixArray";

    private final List<ITransformer> transformers = new ArrayList<>();

    public JechClassTransformer(List<ITransformer> transformers, JsonObject json) {
        this.transformers.addAll(transformers);
        Set<String> removals = new HashSet<>();
        if (!json.entrySet().isEmpty()) {
            json.get("removals").getAsJsonArray().forEach(it -> {
                String name = it.getAsString();
                removals.add(name);
            });
            transformers.forEach(it -> it.init(json, removals));
        }
        suffixClassName = json.get("suffixClassName").getAsString();
    }

    public boolean transform(ClassNode node) {
        boolean transformed = false;
        for (ITransformer transformer : transformers) {
            if (transformer.accept(node.name)) {
                transformer.transform(node);
                transformed = true;
                LOGGER.debug("Transformed " + node.name + " with " + transformer.getClass().getSimpleName());
            }
        }
        return transformed;
    }

    public List<ITransformer> getTransformers() {
        return transformers;
    }
}
