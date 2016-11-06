package towdium.je_characters;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class ClassTransformer implements IClassTransformer {
    HashMap<String, String> classToMod;
    HashMap<String, String> modToMethod;
    HashMap<String, Consumer<MethodNode>> modToConsumer;
    Consumer<MethodNode> consStr = ClassTransformer::transformStr;
    Consumer<MethodNode> consReg = ClassTransformer::transformReg;

    {
        classToMod = new HashMap<>();
        classToMod.put("mezz.jei.ItemFilter$FilterPredicate", "JEI");
        classToMod.put("appeng.client.me.ItemRepo", "AE2");
        modToMethod = new HashMap<>();
        modToMethod.put("JEI", "stringContainsTokens");
        modToMethod.put("AE2", "updateView");
        modToConsumer = new HashMap<>();
        modToConsumer.put("JEI", consStr);
        modToConsumer.put("AE2", consReg);
    }

    static void transformStr(MethodNode methodNode) {
        transform(methodNode, "java/lang/String", "contains", "towdium/je_characters/CheckHelper", "check", "(Ljava/lang/String;Ljava/lang/String;)Z", false, Opcodes.INVOKESTATIC);
    }

    static void transformReg(MethodNode methodNode) {
        transform(methodNode, "java/util/regex/Pattern", "matcher", "towdium/je_characters/CheckHelperAdditions", "checkReg", "(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;", false, Opcodes.INVOKESTATIC);

    }

    static void transform(MethodNode methodNode, String owner, String name, String newOwner, String newName, String id, boolean isInterface, int op) {
        Iterator<AbstractInsnNode> i2 = methodNode.instructions.iterator();
        while (i2.hasNext()) {
            AbstractInsnNode node = i2.next();
            if (node instanceof MethodInsnNode && node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                MethodInsnNode insnNode = ((MethodInsnNode) node);
                if (insnNode.owner.equals(owner) && insnNode.name.equals(name)) {
                    methodNode.instructions.set(insnNode, new MethodInsnNode(op, newOwner, newName, id, isInterface));
                }
            }
        }
    }

    @Override
    public byte[] transform(String s, String s1, byte[] bytes) {
        String mod = classToMod.get(s1);
        if (mod != null) {
            ClassNode classNode = new ClassNode();
            ClassReader classReader = new ClassReader(bytes);
            classReader.accept(classNode, 0);
            classNode.methods.stream().filter(methodNode -> methodNode.name.equals(modToMethod.get(mod))).
                    forEach(methodNode -> modToConsumer.get(mod).accept(methodNode));
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            classNode.accept(classWriter);
            return classWriter.toByteArray();
        }
        return bytes;
    }
}
