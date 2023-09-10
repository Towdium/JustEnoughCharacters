package me.towdium.jecharacters.asm;

import me.towdium.jecharacters.annotations.ParametersAreNonnullByDefault;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@ParametersAreNonnullByDefault
public class JechClassTransformer {

    /**
     * Set by different environments.
     * Default: Forge 1.20.1
     */
    public static String suffixClassName = "net/minecraft/client/searchtree/SuffixArray";

    private final Map<String, Set<TransformTarget>> targets = new HashMap<>();
    private final Map<String, ICustomTransformer> custom = new HashMap<>();

    public static JechClassTransformer INSTANCE = new JechClassTransformer();

    private JechClassTransformer() {

    }

    public void transform(ClassNode node) {
        Set<TransformTarget> targetMethods = targets.get(node.name);
        if (targetMethods == null) {
            ICustomTransformer customTransformer = custom.get(node.name);
            if (customTransformer == null) return;
            else customTransformer.transform(node);
            return;
        }

        for (MethodNode method : node.methods) {
            for (TransformTarget target : targetMethods) {
                if (target.matches(node.name, method)) {
                    if (target.getType() == TransformTarget.Type.SUFFIX)
                        TransformUtils.transformSuffix(method, suffixClassName);
                    else
                        getTransformer(target.getType()).accept(method);
                }
            }
        }
    }

    public void registerCustomTransformer(String className, ICustomTransformer transformer) {
        if (custom.containsKey(className))
            throw new RuntimeException("Duplicate custom transformer: " + className);

        custom.put(className, transformer);
    }

    public void registerTransformTarget(String className, TransformTarget target) {
        targets.computeIfAbsent(className, s -> new HashSet<>()).add(target);
    }

    private static Consumer<MethodNode> getTransformer(TransformTarget.Type type) {
        switch (type) {
            case CONTAINS:
                return TransformUtils::transformContains;
            case EQUALS:
                return TransformUtils::transformEquals;
            case REGEXP:
                return TransformUtils::transformRegExp;
            default:
                return method -> {

                };
        }
    }

    public Map<String, Set<TransformTarget>> getTargets() {
        return targets;
    }

    public Map<String, ICustomTransformer> getCustom() {
        return custom;
    }
}
