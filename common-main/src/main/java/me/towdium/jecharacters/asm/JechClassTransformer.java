package me.towdium.jecharacters.asm;

import me.towdium.jecharacters.annotations.ParametersAreNonnullByDefault;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

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
    public static final JechClassTransformer INSTANCE = new JechClassTransformer();

    private JechClassTransformer() {
        ServiceLoader.load(ITransformer.class).forEach(transformers::add);
    }

    public void transform(ClassNode node) {
        for (ITransformer transformer : transformers) {
            if (transformer.accept(node))
                transformer.transform(node);
        }
    }

    public List<ITransformer> getTransformers() {
        return transformers;
    }
}
