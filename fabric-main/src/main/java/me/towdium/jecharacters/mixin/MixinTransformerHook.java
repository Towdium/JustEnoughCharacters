package me.towdium.jecharacters.mixin;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import lombok.experimental.FieldDefaults;
import me.towdium.jecharacters.asm.JechClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.transformers.TreeTransformer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

@FieldDefaults(
        level = AccessLevel.PRIVATE,
        makeFinal = true
)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class MixinTransformerDelegate<T extends TreeTransformer & IMixinTransformer> extends TreeTransformer implements IMixinTransformer {

    @Delegate(types = {TreeTransformer.class, IMixinTransformer.class})
    T delegate;

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
