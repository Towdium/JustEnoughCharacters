
package me.towdium.jecharacters.asm.transformers;

import com.google.auto.service.AutoService;
import me.towdium.jecharacters.asm.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Set;

@AutoService(Transformer.class)
public final class HexcessibleTransformer implements Transformer {

    private static final Set<String> TARGET_CLASSES = Set.of("dev/tizu/hexcessible/Utils");

    @Override
    public ClassNode transform(ClassNode node) {
        Transformer.findMethod(
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
    public Set<String> targetClasses() {
        return TARGET_CLASSES;
    }
}