package me.towdium.jecharacters.asm;

import me.towdium.jecharacters.annotations.MethodsReturnNonnullByDefault;
import me.towdium.jecharacters.annotations.ParametersAreNonnullByDefault;
import org.objectweb.asm.tree.ClassNode;

import java.util.Set;

/**
 * Used for special compatibility with mods like jei.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public interface ICustomTransformer {

    Set<String> targetClasses();

    default boolean accept(ClassNode node) {
        return targetClasses().contains(node.name);
    }

    void transform(ClassNode node);
}
