package me.towdium.jecharacters.core;

import me.towdium.jecharacters.safe.JustEnoughCharacters;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import javax.annotation.Nullable;
import java.util.Iterator;

public abstract class Utilities {
    public static boolean transformInvoke(
            MethodNode methodNode, String owner, String name, String newOwner, String newName,
            String id, boolean isInterface, int op, @Nullable String arg1, @Nullable String arg2) {
        JustEnoughCharacters.logger.info("Transforming invoke of " + owner + "." + name +
                " to " + newOwner + "." + newName + " in method " + methodNode.name + ".");

        Iterator<AbstractInsnNode> iterator = methodNode.instructions.iterator();

        boolean ret = false;
        while (iterator.hasNext()) {
            AbstractInsnNode node = iterator.next();
            if (node instanceof MethodInsnNode && (node.getOpcode() == Opcodes.INVOKEVIRTUAL ||
                    node.getOpcode() == Opcodes.INVOKESPECIAL || node.getOpcode() == Opcodes.INVOKESTATIC)) {
                MethodInsnNode insnNode = ((MethodInsnNode) node);
                if (insnNode.owner.equals(owner) && insnNode.name.equals(name)) {
                    methodNode.instructions.set(insnNode, new MethodInsnNode(op, newOwner, newName, id, isInterface));
                    ret = true;
                }
            }
            if (node instanceof InvokeDynamicInsnNode && node.getOpcode() == Opcodes.INVOKEDYNAMIC
                    && arg1 != null && arg2 != null) {
                InvokeDynamicInsnNode insnNode = ((InvokeDynamicInsnNode) node);
                if (insnNode.bsmArgs[1] instanceof Handle) {
                    Handle h = ((Handle) insnNode.bsmArgs[1]);
                    if (h.getOwner().equals(owner) && h.getName().equals(name)) {
                        @SuppressWarnings("deprecation") Object[] args = {Type.getType(arg1),
                                new Handle(6, newOwner, newName, id), Type.getType(arg2)};
                        methodNode.instructions.set(insnNode,
                                new InvokeDynamicInsnNode(insnNode.name, insnNode.desc, insnNode.bsm, args));
                        ret = true;
                    }
                }
            }
        }
        return ret;
    }

    public static void transformConstruct(MethodNode methodNode, String desc, String destNew) {
        JustEnoughCharacters.logger.info("Transforming constructor of " + desc +
                " to " + destNew + " in method " + methodNode.name + ".");
        Iterator<AbstractInsnNode> i = methodNode.instructions.iterator();
        int cnt = 0;
        while (i.hasNext()) {
            AbstractInsnNode node = i.next();
            if (node.getOpcode() == Opcodes.NEW) {
                TypeInsnNode nodeNew = ((TypeInsnNode) node);
                if (nodeNew.desc.equals(desc)) {
                    nodeNew.desc = destNew;
                    cnt++;
                }
            } else if (node.getOpcode() == Opcodes.INVOKESPECIAL) {
                MethodInsnNode nodeNew = ((MethodInsnNode) node);
                if (nodeNew.owner.equals(desc)) nodeNew.owner = destNew;
            }
        }
        JustEnoughCharacters.logger.info("Transformed " + cnt + " occurrences.");
    }

    public static void transformHook(MethodNode methodNode, String owner, String name, String id) {
        Iterator<AbstractInsnNode> i = methodNode.instructions.iterator();
        while (i.hasNext()) {
            AbstractInsnNode node = i.next();
            if (node instanceof InsnNode && node.getOpcode() == Opcodes.RETURN) {
                methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKESTATIC,
                        owner, name, id, false));
            }
        }
    }
}
