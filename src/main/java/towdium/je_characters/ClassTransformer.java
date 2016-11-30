package towdium.je_characters;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class ClassTransformer implements IClassTransformer {
    public static Map<String, MethodWrapper> m = new HashMap<>();
    public static Set<String> s = new HashSet<>();
    static boolean flag = false;

    static void transformStr(MethodNode methodNode) {
        transform(
                methodNode, "java/lang/String", "contains", "towdium/je_characters/CheckHelper", "checkStr",
                "(Ljava/lang/String;Ljava/lang/String;)Z", false, Opcodes.INVOKESTATIC
        );
    }

    static void transformReg(MethodNode methodNode) {
        transform(methodNode, "java/util/regex/Pattern", "matcher", "towdium/je_characters/CheckHelper", "checkReg",
                "(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;", false, Opcodes.INVOKESTATIC
        );
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
        if (LoadingPlugin.initialized) {
            if (ClassTransformer.s.contains(s1)) {
                ClassNode classNode = new ClassNode();
                ClassReader classReader = new ClassReader(bytes);
                classReader.accept(classNode, 0);
                LoadingPlugin.log.info("[je_characters] Dump methods in class \"" + s1 + "\".");
                classNode.methods.forEach(methodNode -> LoadingPlugin.log.info("[je_characters]\t" + methodNode.name));
            }

            MethodWrapper mw = m.get(s1);
            if (mw != null) {
                ClassNode classNode = new ClassNode();
                ClassReader classReader = new ClassReader(bytes);
                classReader.accept(classNode, 0);
                LoadingPlugin.log.info("[je_characters] Transforming class \"" + mw.className + "\".");
                classNode.methods.stream().filter(methodNode -> methodNode.name.equals(mw.methodName)).
                        forEach(methodNode -> {
                            mw.transformer.accept(methodNode);
                            flag = true;
                        });
                LoadingPlugin.log.info("[je_characters] " + (flag ? "Succeeded." : ("Method \"") + mw.methodName + "\" not found."));
                ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                classNode.accept(classWriter);
                return classWriter.toByteArray();
            }
            return bytes;
        } else {
            return bytes;
        }
    }
}
