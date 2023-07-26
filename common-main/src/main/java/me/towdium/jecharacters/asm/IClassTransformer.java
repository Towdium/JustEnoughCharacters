package me.towdium.jecharacters.asm;

import org.objectweb.asm.tree.ClassNode;

public interface IClassTransformer {

    void transform(ClassNode node);

}
