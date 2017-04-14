package towdium.je_characters.jei;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import towdium.je_characters.JECConfig;
import towdium.je_characters.LoadingPlugin;

import java.util.Iterator;

/**
 * Author: towdium
 * Date:   09/01/17
 */
public class TransformHelper {
    static final String CLASS_NAME = "mezz.jei.ItemFilterInternals";
    public static boolean withJei = false;

    public static String getClassName() {
        return CLASS_NAME;
    }

    public static byte[] transform(byte[] code) {
        if (!JECConfig.EnumItems.EnableJEI.getProperty().getBoolean()) {
            return code;
        }
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(code);
        classReader.accept(classNode, 0);
        classNode.methods.stream().filter(method -> method.name.equals("<init>")).forEach(method -> {
            LoadingPlugin.log.info("Transforming JEI. Injecting data structure.");
            Iterator<AbstractInsnNode> i = method.instructions.iterator();
            while (i.hasNext()) {
                AbstractInsnNode node = i.next();
                if (node.getOpcode() == Opcodes.NEW) {
                    TypeInsnNode nodeNew = ((TypeInsnNode) node);
                    if (nodeNew.desc.equals("com/abahgat/suffixtree/GeneralizedSuffixTree")) {
                        nodeNew.desc = "towdium/je_characters/jei/MyFilter";
                    }
                } else if (node.getOpcode() == Opcodes.INVOKESPECIAL) {
                    MethodInsnNode nodeNew = ((MethodInsnNode) node);
                    if (nodeNew.owner.equals("com/abahgat/suffixtree/GeneralizedSuffixTree")) {
                        nodeNew.owner = "towdium/je_characters/jei/MyFilter";
                    }
                }
            }
        });
        classNode.methods.stream().filter(method -> method.name.equals("buildSuffixTrees")).forEach(method -> {
            LoadingPlugin.log.info("Transforming JEI. Applying cache hooks.");
            Iterator<AbstractInsnNode> i = method.instructions.iterator();
            while (i.hasNext()) {
                AbstractInsnNode node = i.next();
                if (node instanceof InsnNode && node.getOpcode() == Opcodes.RETURN) {
                    method.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKESTATIC, "towdium/je_characters/jei/TransformHelper", "loadingHook", "()V", false));
                }
            }

        });
        withJei = true;
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(classWriter);
        return classWriter.toByteArray();
    }

    public static void loadingHook() {
        MyFilter.onBuildFinished();
    }
}
