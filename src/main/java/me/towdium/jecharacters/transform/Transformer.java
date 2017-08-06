package me.towdium.jecharacters.transform;

import com.google.common.collect.HashMultimap;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.util.Wrapper;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
@SuppressWarnings("SameParameterValue")
public interface Transformer {
    Wrapper<MethodNode> methodNode = new Wrapper<>(null);

    static Optional<MethodNode> findMethod(ClassNode c, String name) {
        methodNode.v = null;
        c.methods.stream().filter(methodNode -> methodNode.name.equals(name))
                .forEach(methodNode -> Transformer.methodNode.v = methodNode);
        return Optional.ofNullable(methodNode.v);
    }

    static Optional<MethodNode> findMethod(ClassNode c, String name, String desc) {
        methodNode.v = null;
        c.methods.stream().filter(methodNode -> methodNode.name.equals(name))
                .filter(methodNode -> methodNode.desc.equals(desc))
                .forEach(methodNode -> Transformer.methodNode.v = methodNode);
        return Optional.ofNullable(methodNode.v);
    }

    static boolean transformInvoke(
            MethodNode methodNode, String owner, String name, String newOwner, String newName,
            String id, boolean isInterface, int op, @Nullable String arg1, @Nullable String arg2) {
        Iterator<AbstractInsnNode> iterator = methodNode.instructions.iterator();
        boolean ret = false;
        while (iterator.hasNext()) {
            AbstractInsnNode node = iterator.next();
            if (node instanceof MethodInsnNode && node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
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

    static void transformConstruct(MethodNode methodNode, String desc, String destNew) {
        Iterator<AbstractInsnNode> i = methodNode.instructions.iterator();
        while (i.hasNext()) {
            AbstractInsnNode node = i.next();
            if (node.getOpcode() == Opcodes.NEW) {
                TypeInsnNode nodeNew = ((TypeInsnNode) node);
                if (nodeNew.desc.equals(desc)) {
                    // JechCore.LOG.info("Transforming new " + desc);
                    nodeNew.desc = destNew;
                }
            } else if (node.getOpcode() == Opcodes.INVOKESPECIAL) {
                MethodInsnNode nodeNew = ((MethodInsnNode) node);
                if (nodeNew.owner.equals(desc)) {
                    // JechCore.LOG.info("Transforming constructor " + desc);
                    nodeNew.owner = destNew;
                }
            }
        }
    }

    static void transformHook(MethodNode methodNode, String owner, String name, String id) {
        Iterator<AbstractInsnNode> i = methodNode.instructions.iterator();
        while (i.hasNext()) {
            AbstractInsnNode node = i.next();
            if (node instanceof InsnNode && node.getOpcode() == Opcodes.RETURN) {
                methodNode.instructions.insertBefore(node, new MethodInsnNode(Opcodes.INVOKESTATIC,
                        owner, name, id, false));
            }
        }
    }

    boolean accepts(String name);

    byte[] transform(byte[] bytes);

    interface Extended extends Transformer {
        default byte[] transform(byte[] bytes) {
            ClassNode classNode = new ClassNode();
            ClassReader classReader = new ClassReader(bytes);
            classReader.accept(classNode, 0);
            transform(classNode);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(classWriter);
            return classWriter.toByteArray();
        }

        void transform(ClassNode n);
    }

    class MethodDecoder {
        public final static Consumer<String> LOGGER = s -> JechCore.LOG.info("Invalid config syntax: " + s);
        HashMultimap<String, String> methods = HashMultimap.create();

        public void addAll(String[] names, Consumer<String> callback) {
            for (String s : names) {
                String[] ss = s.split(":");
                if (ss.length == 2)
                    methods.put(ss[0], ss[1]);
                else
                    callback.accept(s);
            }
        }

        public void removeAll(String[] names, Consumer<String> callback) {
            for (String s : names) {
                String[] ss = s.split(":");
                if (ss.length == 2)
                    methods.remove(ss[0], ss[1]);
                else
                    callback.accept(s);
            }
        }

        public Set<String> getMethodsForClass(String c) {
            return methods.get(c);
        }

        public boolean contains(String s) {
            return methods.containsKey(s);
        }
    }
}
