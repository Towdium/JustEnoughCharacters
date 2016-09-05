package towdium.je_characters;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import net.minecraft.launchwrapper.IClassTransformer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class ClassTransformer implements IClassTransformer {
    static List<String> names = new ArrayList<String>();
    static final String CLASS_NAME = "mezz.jei.ItemFilter$FilterPredicate";
    static final String METHOD_NAME = "stringContainsTokens";

    @Override
    public byte[] transform(String s, String s1, byte[] bytes) {
        if(s1.equals(CLASS_NAME)) {
            ClassNode classNode = new ClassNode();
            ClassReader classReader = new ClassReader(bytes);
            classReader.accept(classNode, 0);
            for(MethodNode methodNode : classNode.methods) {
                if(methodNode.name.equals(METHOD_NAME)) {
                    Iterator<AbstractInsnNode> i2 = methodNode.instructions.iterator();
                    while(i2.hasNext()) {
                        AbstractInsnNode node = i2.next();
                        if (node instanceof MethodInsnNode && node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                            MethodInsnNode insnNode = ((MethodInsnNode) node);
                            if (insnNode.owner.equals("java/lang/String") && insnNode.name.equals("contains")) {
                                methodNode.instructions.set(insnNode, new MethodInsnNode(Opcodes.INVOKESTATIC, "towdium/je_characters/CheckHelper", "check", "(Ljava/lang/String;Ljava/lang/String;)Z", false));
                                ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                                classNode.accept(classWriter);
                                return classWriter.toByteArray();
                            }
                        }
                    }
                }
            }
        }
        return bytes;
    }
}
