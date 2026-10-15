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

/**
 * The two bytecode shapes the harness's coremod builds. Both are deliberately tiny: the whole seam
 * is either a call retargeted at a harness class or one extra call at a method's return sites.
 *
 * <p><b>Call-site redirection (the input seam).</b> Every {@code INVOKESTATIC} of an
 * {@code org.lwjgl.input.Keyboard}/{@code Mouse} read inside a game class is retargeted at
 * {@link me.towdium.jecharacters.uitest.input.LwjglKeyboardSeam}/{@code LwjglMouseSeam}, which
 * forwards to the real LWJGL method whenever the harness is not driving:
 *
 * <pre>{@code
 * while (Keyboard.next())        ->  while (LwjglKeyboardSeam.next())
 * int key = Keyboard.getEventKey() -> int key = LwjglKeyboardSeam.getEventKey()
 * }</pre>
 *
 * <p>The descriptor and the exception table are untouched — only the constant-pool reference
 * changes — so the retargeted call is as cheap as the original and every surrounding frame stays
 * valid. That is why the seam could not be implemented by rewriting the LWJGL classes themselves:
 * {@code LaunchClassLoader} hardcodes {@code org.lwjgl.} among its class-loader exclusions and
 * hands those names to its parent loader untouched (launchwrapper 1.12,
 * {@code LaunchClassLoader.findClass}), so no coremod transformer ever sees them. Retargeting the
 * readers needs no such access, and it is also the closer analogue of 1.21.1, whose seam hooks
 * vanilla's own wrapper classes rather than GLFW.
 *
 * <p><b>Return-site injection (the recorder).</b> At every {@code RETURN} the method's answer is
 * parked in a scratch local, the hook is called as
 * {@code hook(<arguments...>, <return value>)}, and the answer is pushed back for the method's own
 * return. The scratch local sits past the method's last declared local, in straight-line code with
 * no new branch targets, so the class's existing StackMapTable stays valid and — {@code Match} and
 * friends being Java 8 classes — the verifier's dataflow is happy with the store/load pair.
 * Arguments are read from their parameter slots, which a compiler never reuses for another local,
 * and are therefore still in scope at the return; an instance method's slot 0 is {@code this} and is
 * skipped.
 */
public final class Rewriter {

    private Rewriter() {}

    /** Finds a method by name and descriptor, or {@code null}. */
    public static MethodNode find(ClassNode owner, String name, String desc) {
        for (MethodNode method : owner.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) return method;
        }
        return null;
    }

    /**
     * Retargets every static call to {@code fromOwner} whose {@code name + descriptor} is in
     * {@code reads} so it calls {@code seamOwner} instead. Only the reference itself changes: the
     * call's name, descriptor, arguments on the stack and exception table are all left alone, so
     * this is safe in any method, compiled by any compiler.
     *
     * @return the number of call sites retargeted
     */
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

    /**
     * Inserts {@code hookOwner.hookName(arguments..., return value)} before every {@code RETURN} of
     * the method. The hook's descriptor is the method's own descriptor with the return type appended
     * and a {@code V} return, so the handler sees exactly what the method saw and answered.
     *
     * <p>The answer cannot simply be {@code DUP}ped: duplicating it puts the copy <em>under</em> the
     * argument loads, so the hook would read the arguments in the wrong slots (and a verifier would
     * reject the call outright). It is parked in a scratch local instead, which also makes the
     * instrumentation independent of the answer's category.
     *
     * @return the number of return sites instrumented
     */
    public static int injectAtReturns(MethodNode method, String hookOwner, String hookName) {
        Type[] arguments = Type.getArgumentTypes(method.desc);
        Type result = Type.getReturnType(method.desc);
        boolean voidMethod = result.equals(Type.VOID_TYPE);
        // Slot 0 is `this` in an instance method, and the hooked searches on this line are instance
        // methods (Match$FakeTree, Match$FakeArray, JechJeiPlugin$JechSearchIndex), so the first
        // argument does not always live in slot 0.
        int slot = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        // The scratch slot lives past the method's own locals, so no parameter or compiler local is
        // ever clobbered.
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
                // Put the answer back where the method's own RETURN expects it.
                body.insertBefore(node, new VarInsnNode(result.getOpcode(Opcodes.ILOAD), scratch));
            }
            injected++;
        }
        return injected;
    }
}
