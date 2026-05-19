package me.towdium.jecharacters.mixin;

import me.towdium.jecharacters.asm.JechClassTransformer;
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


    private final T delegate;

    MixinTransformerDelegate(T delegate) {
        this.delegate = delegate;
    }

    @Override
    public void audit(MixinEnvironment environment) {
        delegate.audit(environment);
    }

    @Override
    public List<String> reload(String mixinClass, ClassNode classNode) {
        return delegate.reload(mixinClass, classNode);
    }

    @Override
    public boolean computeFramesForClass(MixinEnvironment environment, String name, ClassNode classNode) {
        return delegate.computeFramesForClass(environment, name, classNode);
    }

    @Override
    public byte[] transformClass(MixinEnvironment environment, String name, byte[] classBytes) {
        return delegate.transformClass(environment, name, classBytes);
    }

    @Override
    public boolean transformClass(MixinEnvironment environment, String name, ClassNode classNode) {
        return delegate.transformClass(environment, name, classNode);
    }

    @Override
    public boolean couldTransformClass(MixinEnvironment environment, String name) {
        return delegate.couldTransformClass(environment, name);
    }

    @Override
    public byte[] generateClass(MixinEnvironment environment, String name) {
        return delegate.generateClass(environment, name);
    }

    @Override
    public boolean generateClass(MixinEnvironment environment, String name, ClassNode classNode) {
        return delegate.generateClass(environment, name, classNode);
    }

    @Override
    public IExtensionRegistry getExtensions() {
        return delegate.getExtensions();
    }

    @Override
    public byte[] transformClassBytes(String name, String transformedName, byte[] basicClass) {
        return delegate.transformClassBytes(name, transformedName, basicClass);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public boolean isDelegationExcluded() {
        return delegate.isDelegationExcluded();
    }
}

class MixinTransformerHook<T extends TreeTransformer & IMixinTransformer> extends MixinTransformerDelegate<T> {

    private final Deque<String> transformationStack = new ArrayDeque<>();
    private final JechClassTransformer transformer;

    MixinTransformerHook(T delegate, JechClassTransformer transformer) {
        super(delegate);
        this.transformer = transformer;
    }

    @Override
    public byte[] transformClassBytes(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || Objects.equals(transformationStack.peek(), name))
            return super.transformClassBytes(name, transformedName, basicClass);
        transformationStack.push(name);
        basicClass = super.transformClassBytes(name, transformedName, basicClass);
        String internalName = name.replace('.', '/');
        boolean shouldTransform = transformer.getTransformers().stream().anyMatch(it -> it.accept(internalName));
        if (!shouldTransform) return basicClass;
        //transform class bytes
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(basicClass);
        classReader.accept(classNode, 0);
        transformer.transform(classNode);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(classWriter);
        basicClass = classWriter.toByteArray();
        transformationStack.pop();
        return basicClass;
    }


}
