package me.towdium.jecharacters.transformers;

import com.google.gson.JsonObject;
import me.towdium.jecharacters.asm.ITransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Set;

public final class HexcessibleTransformer implements ITransformer {
    private static final String TARGET_CLASS =
            "dev/tizu/hexcessible/Utils";

    private static final Set<String> TARGET_CLASSES =
            Set.of(TARGET_CLASS);

    @Override
    public ClassNode transform(ClassNode node) {
        ITransformer.findMethod(
                node,
                "fluffySearch",
                "(Ljava/lang/String;Ljava/lang/String;)I"
        ).ifPresent(method -> {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() != Opcodes.IRETURN) {
                    continue;
                }

                /*
                 * Stack before injection:
                 *   [originalScore]
                 *
                 * Stack before invocation:
                 *   [originalScore, query, candidate]
                 */
                InsnList injected = new InsnList();
                injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 1));
                injected.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "me/towdium/jecharacters/utils/HexcessibleHooks",
                        "mergeScore",
                        "(ILjava/lang/String;Ljava/lang/String;)I",
                        false
                ));

                method.instructions.insertBefore(instruction, injected);
            }
        });

        return node;
    }

    @Override
    public boolean accept(String className) {
        return TARGET_CLASS.equals(className);
    }

    @Override
    public void init(JsonObject config, Set<String> removal) {

    }

    @Override
    public Set<String> targetClasses() {
        return TARGET_CLASSES;
    }
}