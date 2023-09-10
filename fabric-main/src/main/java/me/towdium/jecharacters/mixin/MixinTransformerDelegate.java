package me.towdium.jecharacters.mixin;

import me.towdium.jecharacters.asm.JechClassTransformer;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.IExtensionRegistry;
import org.spongepowered.asm.transformers.TreeTransformer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

class MixinTransformerDelegate<T extends TreeTransformer & IMixinTransformer> extends TreeTransformer implements IMixinTransformer {

    private final Deque<String> transformationStack = new ArrayDeque<>();
    @NotNull
    private final T origin;

    public MixinTransformerDelegate(@NotNull T origin) {
        this.origin = origin;
    }

    @Override
    public void audit(MixinEnvironment environment) {
        origin.audit(environment);
    }

    @Override
    public List<String> reload(String mixinClass, ClassNode classNode) {
        return origin.reload(mixinClass, classNode);
    }

    @Override
    public IExtensionRegistry getExtensions() {
        return this.origin.getExtensions();
    }

    @Override
    public byte[] transformClassBytes(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || Objects.equals(transformationStack.peek(), name))
            return origin.transformClassBytes(name, transformedName, basicClass);
        transformationStack.push(name);
        basicClass = this.origin.transformClassBytes(name, transformedName, basicClass);
        //transform class bytes
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(basicClass);
        classReader.accept(classNode, 0);
        JechClassTransformer.INSTANCE.transform(classNode);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(classWriter);
        basicClass = classWriter.toByteArray();
        transformationStack.pop();
        return basicClass;
    }

    @Override
    public String getName() {
        return this.origin.getName();
    }

    @Override
    public boolean isDelegationExcluded() {
        return this.origin.isDelegationExcluded();
    }
}
