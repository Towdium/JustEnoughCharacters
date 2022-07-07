package me.towdium.hecharacters.transform;

import com.google.common.collect.HashMultimap;
import me.towdium.hecharacters.HechConfig;
import me.towdium.hecharacters.core.HechCore;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
@SuppressWarnings("SameParameterValue")
public interface Transformer {

    static Optional<MethodNode> findMethod(ClassNode c, String name) {
        Optional<MethodNode> ret = c.methods.stream().filter(methodNode -> methodNode.name.equals(name))
                .findFirst();
        String s = ret.isPresent() ? "," : ", not";
        HechCore.LOG.info("Finding method " + name + " in class " + c.name + s + " found.");
        return ret;
    }

    static Optional<MethodNode> findMethod(ClassNode c, String name, String desc) {
        Optional<MethodNode> ret = c.methods.stream().filter(methodNode -> methodNode.name.equals(name))
                .filter(methodNode -> methodNode.desc.equals(desc)).findFirst();
        String s = ret.isPresent() ? "," : ", not";
        HechCore.LOG.info("Finding method " + name + desc + " in class " + c.name + s + " found.");
        return ret;
    }

    static boolean transformInvoke(
            MethodNode methodNode, String owner, String name, String newOwner, String newName,
            String id, boolean isInterface, int op, @Nullable String arg1, @Nullable String arg2) {
        HechCore.LOG.info("Transforming invoke of " + owner + "." + name +
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

    static void transformConstruct(MethodNode methodNode, String desc, String destNew) {
        HechCore.LOG.info("Transforming constructor of " + desc +
                " to " + destNew + " in method " + methodNode.name + ".");
        Iterator<AbstractInsnNode> i = methodNode.instructions.iterator();
        int cnt = 0;
        while (i.hasNext()) {
            AbstractInsnNode node = i.next();
            if (node.getOpcode() == Opcodes.NEW) {
                TypeInsnNode nodeNew = ((TypeInsnNode) node);
                if (nodeNew.desc.equals(desc)) {
                    // HechCore.LOG.info("Transforming new " + desc);
                    nodeNew.desc = destNew;
                    cnt++;
                }
            } else if (node.getOpcode() == Opcodes.INVOKESPECIAL) {
                MethodInsnNode nodeNew = ((MethodInsnNode) node);
                if (nodeNew.owner.equals(desc)) {
                    // HechCore.LOG.info("Transforming constructor " + desc);
                    nodeNew.owner = destNew;
                }
            }
        }
        HechCore.LOG.info("Transformed " + cnt + " occurrences.");
    }

    static boolean transformInvokeLambda(
            MethodNode method, String owner, String name, String desc,
            String newOwner, String newName, String newDesc
    ) {
        boolean ret = false;
        Iterator<AbstractInsnNode> i = method.instructions.iterator();
        while (i.hasNext()) {
            AbstractInsnNode node = i.next();
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

    abstract class Default implements Transformer {
        @Override
        public byte[] transform(byte[] bytes) {
            ClassNode classNode = new ClassNode();
            ClassReader classReader = new ClassReader(bytes);
            classReader.accept(classNode, 0);
            transform(classNode);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(classWriter);
            return classWriter.toByteArray();
        }

        protected abstract void transform(ClassNode n);
    }

    abstract class Configurable extends Default {
        protected MethodDecoder md = new MethodDecoder();

        protected abstract String[] getDefault();

        protected abstract String[] getAdditional();

        protected abstract String getName();

        protected abstract void transform(MethodNode n);

        @Override
        protected void transform(ClassNode c) {
            HechCore.LOG.info("Transforming class " + c.name + " for " + getName() + ".");
            Set<String> ms = md.getMethodsForClass(c.name.replace('/', '.'));
            if (!ms.isEmpty()) c.methods.stream().filter(m -> ms.contains(m.name)).forEach(this::transform);
            else HechCore.LOG.info("No function matched in class " + c.name);
        }

        @Override
        public boolean accepts(String name) {
            return md.contains(name);
        }

        public void reload() {
            MethodDecoder mdt = new MethodDecoder();
            mdt.addAll(getDefault());
            mdt.addAll(getAdditional());
            mdt.removeAll(HechConfig.listMethodBlacklist);
            md = mdt;
        }
    }

    class MethodDecoder {
        HashMultimap<String, String> methods = HashMultimap.create();

        public static void logError(String s) {
            HechCore.LOG.info("Invalid config syntax: " + s);
        }

        public void addAll(String[] names) {
            for (String s : names) {
                String[] ss = s.split(":");
                if (ss.length == 2) methods.put(ss[0], ss[1]);
                else logError(s);
            }
        }

        public void removeAll(String[] names) {
            for (String s : names) {
                String[] ss = s.split(":");
                if (ss.length == 2) methods.remove(ss[0], ss[1]);
                else logError(s);
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
