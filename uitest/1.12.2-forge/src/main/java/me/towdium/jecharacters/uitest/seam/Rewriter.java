package me.towdium.jecharacters.uitest.seam;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Set;

public final class Rewriter {

    private Rewriter() {}

    public static MethodNode find(ClassNode owner, String name, String desc) {
        for (MethodNode method : owner.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) return method;
        }
        return null;
    }

    public static int redirect(ClassNode owner, String fromOwner, String seamOwner, Set<String> reads) {
        int retargeted = 0;
        for (MethodNode method : owner.methods) {
            for (AbstractInsnNode node : method.instructions.toArray()) {
                if (!(node instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) node;
                if (call.getOpcode() != Opcodes.INVOKESTATIC) continue;
                if (!call.owner.equals(fromOwner)) continue;
                if (!reads.contains(call.name + call.desc)) continue;
                call.owner = seamOwner;
                retargeted++;
            }
        }
        return retargeted;
    }

    public static int injectAtReturns(MethodNode method, String hookOwner, String hookName) {
        Type[] arguments = Type.getArgumentTypes(method.desc);
        Type result = Type.getReturnType(method.desc);
        boolean voidMethod = result.equals(Type.VOID_TYPE);
        int slot = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        int scratch = method.maxLocals;
        StringBuilder hookDesc = new StringBuilder("(");
        for (Type argument : arguments) hookDesc.append(argument.getDescriptor());
        if (!voidMethod) {
            hookDesc.append(result.getDescriptor());
            method.maxLocals += result.getSize();
        }
        hookDesc.append(")V");
        String desc = hookDesc.toString();

        InsnList body = method.instructions;
        AbstractInsnNode[] snapshot = body.toArray();
        int injected = 0;
        for (AbstractInsnNode node : snapshot) {
            if (node.getOpcode() != Opcodes.RETURN && node.getOpcode() != Opcodes.IRETURN
                    && node.getOpcode() != Opcodes.LRETURN && node.getOpcode() != Opcodes.FRETURN
                    && node.getOpcode() != Opcodes.DRETURN && node.getOpcode() != Opcodes.ARETURN) {
                continue;
            }
            if (!voidMethod) {
                body.insertBefore(node, new VarInsnNode(result.getOpcode(Opcodes.ISTORE), scratch));
            }
            int argumentSlot = slot;
            for (Type argument : arguments) {
                body.insertBefore(node, new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), argumentSlot));
                argumentSlot += argument.getSize();
            }
            if (!voidMethod) {
                body.insertBefore(node, new VarInsnNode(result.getOpcode(Opcodes.ILOAD), scratch));
            }
            body.insertBefore(node, new MethodInsnNode(Opcodes.INVOKESTATIC, hookOwner, hookName, desc, false));
            if (!voidMethod) {
                body.insertBefore(node, new VarInsnNode(result.getOpcode(Opcodes.ILOAD), scratch));
            }
            injected++;
        }
        return injected;
    }
}
