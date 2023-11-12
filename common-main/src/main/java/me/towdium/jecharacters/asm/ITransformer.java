package me.towdium.jecharacters.asm;

import com.google.gson.JsonObject;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.Optional;
import java.util.Set;

public interface ITransformer {

    static Optional<MethodNode> findMethod(ClassNode c, String name) {
        return c.methods.stream()
                .filter(methodNode -> methodNode.name.equals(name))
                .findFirst();
    }

    static Optional<MethodNode> findMethod(ClassNode c, String name, String desc) {
        return c.methods.stream()
                .filter(methodNode -> methodNode.name.equals(name) && methodNode.desc.equals(desc))
                .findFirst();
    }

    static void transformConstruct(MethodNode methodNode, String desc, String destNew) {
        for (AbstractInsnNode node : methodNode.instructions) {
            if (node.getOpcode() == Opcodes.NEW) {
                TypeInsnNode nodeNew = ((TypeInsnNode) node);
                if (nodeNew.desc.equals(desc)) {
                    nodeNew.desc = destNew;
                }
            } else if (node.getOpcode() == Opcodes.INVOKESPECIAL) {
                MethodInsnNode nodeNew = ((MethodInsnNode) node);
                if (nodeNew.owner.equals(desc)) {
                    nodeNew.owner = destNew;
                }
            }
        }
    }

    static void transformInvoke(
            MethodNode methodNode,
            String srcOwner, String srcName, String srcDesc,
            String dstOwner, String dstName, String dstDesc
    ) {

        for (AbstractInsnNode node : methodNode.instructions) {
            if (node instanceof MethodInsnNode && (node.getOpcode() == Opcodes.INVOKEVIRTUAL ||
                    node.getOpcode() == Opcodes.INVOKESPECIAL || node.getOpcode() == Opcodes.INVOKESTATIC)) {
                MethodInsnNode methodInsn = ((MethodInsnNode) node);
                if (methodInsn.owner.equals(srcOwner) && methodInsn.name.equals(srcName) && methodInsn.desc.equals(srcDesc)) {
                    methodInsn.setOpcode(Opcodes.INVOKESTATIC);
                    methodInsn.owner = dstOwner;
                    methodInsn.name = dstName;
                    methodInsn.desc = dstDesc;
                }
            } else if (node instanceof InvokeDynamicInsnNode && node.getOpcode() == Opcodes.INVOKEDYNAMIC) {
                InvokeDynamicInsnNode insnNode = ((InvokeDynamicInsnNode) node);
                if (insnNode.bsmArgs[1] instanceof Handle) {
                    Handle h = ((Handle) insnNode.bsmArgs[1]);
                    if (srcOwner.equals(h.getOwner()) && srcName.equals(h.getName()) && srcDesc.equals(h.getDesc()))
                        insnNode.bsmArgs[1] = new Handle(Opcodes.H_INVOKESTATIC, dstOwner, dstName, dstDesc, false);
                }
            }
        }
    }

    static boolean transformInvokeLambda(
            MethodNode method, String owner, String name, String desc,
            String newOwner, String newName, String newDesc
    ) {
        boolean ret = false;
        for (AbstractInsnNode node : method.instructions) {
            int op = node.getOpcode();
            if (node instanceof InvokeDynamicInsnNode && op == Opcodes.INVOKEDYNAMIC) {
                InvokeDynamicInsnNode insnNode = ((InvokeDynamicInsnNode) node);
                if (insnNode.bsmArgs[1] instanceof Handle) {
                    Handle h = ((Handle) insnNode.bsmArgs[1]);
                    if (!(!h.getOwner().equals(owner) || !h.getName().equals(name) || !h.getDesc().equals(desc))) {
                        insnNode.bsmArgs[1] = new Handle(h.getTag(), newOwner, newName, newDesc, h.isInterface());
                        ret = true;
                    }
                }
            }
        }
        return ret;
    }


    static void injectBeforeReturn(MethodNode methodNode, String owner, String name, String id) {
        for (AbstractInsnNode node : methodNode.instructions) {
            if (node instanceof InsnNode && node.getOpcode() == Opcodes.RETURN) {
                methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKESTATIC,
                        owner, name, id, false));
            }
        }
    }

    static MethodNode transformContains(MethodNode method) {
        transformInvoke(method,
                "java/lang/String",
                "contains",
                "(Ljava/lang/CharSequence;)Z",
                "me/towdium/jecharacters/utils/Match",
                "contains",
                "(Ljava/lang/String;Ljava/lang/CharSequence;)Z"
        );
        transformInvoke(method,
                "kotlin/text/StringsKt",
                "contains",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z",
                "me/towdium/jecharacters/utils/Match",
                "contains",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z"
        );
        transformInvoke(method,
                "kotlin/text/StringsKt",
                "contains",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence)Z",
                "me/towdium/jecharacters/utils/Match",
                "contains",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence)Z"
        );
        return method;
    }

    static MethodNode transformSuffix(MethodNode method, String suffixClass) {
        transformConstruct(method,
                suffixClass,
                "me/towdium/jecharacters/utils/FakeArray"
        );
        return method;
    }

    static MethodNode transformEquals(MethodNode method) {
        transformInvoke(method,
                "java/lang/String",
                "equals",
                "(Ljava/lang/Object;)Z",
                "me/towdium/jecharacters/utils/Match",
                "equals",
                "(Ljava/lang/String;Ljava/lang/Object;)Z"
        );
        return method;
    }

    static MethodNode transformRegExp(MethodNode method) {
        transformInvoke(method,
                "java/util/regex/Pattern",
                "matcher",
                "(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;",
                "me/towdium/jecharacters/utils/Match",
                "matcher",
                "(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;"
        );
        transformInvoke(method,
                "java/lang/String",
                "matches",
                "(Ljava/lang/String;)Z",
                "me/towdium/jecharacters/utils/Match",
                "matches",
                "(Ljava/lang/String;Ljava/lang/String;)Z"
        );
        return method;
    }

    default ClassNode transform(ClassNode node) {
        return node;
    }

    default String internalName(ClassNode node) {
        return node.name.replace('/', '.');
    }

    default boolean accept(String className) {
        return false;
    }

    void init(JsonObject config, Set<String> removal);

    Set<String> targetClasses();

}
