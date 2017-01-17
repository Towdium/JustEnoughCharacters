package towdium.je_characters.jei;

import mezz.jei.ItemFilter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import towdium.je_characters.JECConfig;
import towdium.je_characters.LoadingPlugin;

import java.util.Iterator;

/**
 * Author: towdium
 * Date:   09/01/17
 */
public class TransformHelper {
    static final String CLASS_NAME = "mezz.jei.JeiStarter";

    public static String getClassName() {
        return CLASS_NAME;
    }

    public static byte[] transform(byte[] code) {
        try {
            ItemFilter.class.getConstructor();
        } catch (NoSuchMethodException r) {
            return code;
        }
        if (!JECConfig.EnumItems.EnableJEI.getProperty().getBoolean()) {
            return code;
        }
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(code);
        classReader.accept(classNode, 0);
        classNode.methods.stream().filter(methodNode -> methodNode.name.equals("start")).forEach(methodNode -> {
            LoadingPlugin.log.info("[je_characters] Transforming JEI.");
            Iterator<AbstractInsnNode> i = methodNode.instructions.iterator();
            while (i.hasNext()) {
                AbstractInsnNode node = i.next();
                if (node.getOpcode() == Opcodes.NEW) {
                    TypeInsnNode nodeNew = ((TypeInsnNode) node);
                    if (nodeNew.desc.equals("mezz/jei/ItemFilter")) {
                        nodeNew.desc = "towdium/je_characters/jei/OldItemFilter";
                    }
                } else if (node.getOpcode() == Opcodes.INVOKESPECIAL) {
                    MethodInsnNode nodeNew = ((MethodInsnNode) node);
                    if (nodeNew.owner.equals("mezz/jei/ItemFilter")) {
                        nodeNew.owner = "towdium/je_characters/jei/OldItemFilter";
                    }
                }
            }
        });
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(classWriter);
        return classWriter.toByteArray();
    }
}
