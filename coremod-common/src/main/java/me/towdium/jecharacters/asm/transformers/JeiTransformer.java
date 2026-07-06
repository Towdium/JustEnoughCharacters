package me.towdium.jecharacters.asm.transformers;

import com.google.auto.service.AutoService;
import me.towdium.jecharacters.asm.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Set;

@AutoService(Transformer.class)
public class JeiTransformer implements Transformer {

    private static final Set<String> targets = Set.of(
            "mezz/jei/gui/search/ElementPrefixParser",
            "mezz/jei/gui/ingredients/IngredientFilter",
            "mezz/jei/common/search/GeneralizedSuffixTreeSearchStorage"
    );

    @Override
    public ClassNode transform(ClassNode node) {

        Transformer.findMethod(node, "<clinit>", "()V")
                   .ifPresent(method -> Transformer.transformInvokeLambda(method,
                           "mezz/jei/core/search/suffixtree/GeneralizedSuffixTree",
                           "<init>",
                           "()V",
                           "me/towdium/jecharacters/utils/FakeTree",
                           "<init>",
                           "()V"
                   ));

        Transformer.findMethod(node, "<init>", "(Lmezz/jei/api/runtime/IIngredientManager;Lmezz/jei/common/config/IIngredientFilterConfig;Lmezz/jei/api/helpers/IColorHelper;Lmezz/jei/api/helpers/IModIdHelper;)V")
                   .ifPresent(method -> Transformer.transformInvokeLambda(method,
                           "mezz/jei/core/search/suffixtree/GeneralizedSuffixTree",
                           "<init>",
                           "()V",
                           "me/towdium/jecharacters/utils/FakeTree",
                           "<init>",
                           "()V"
                   ));

        Transformer.findMethod(node, "<init>", "()V")
                   .ifPresent(method -> Transformer.transformConstruct(method,
                           "net/mezzdev/suffixtree/GeneralizedSuffixTree",
                           "me/towdium/jecharacters/utils/FakeTree2"
                   ));

        Transformer.findMethod(node, "parseSearchTokens", "(Ljava/lang/String;)Lmezz/jei/gui/ingredients/IngredientFilter$SearchTokens;")
                   .ifPresent(method -> {
                               InsnList list = method.instructions;
                               for (int i = 0; i < list.size(); i++) {
                                   AbstractInsnNode insnNode = list.get(i);
                                   if (insnNode.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                                       MethodInsnNode methodInsn = (MethodInsnNode) insnNode;
                                       if ("java/util/regex/Pattern".equals(methodInsn.owner) && "matcher".equals(methodInsn.name) && "(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;".equals(methodInsn.desc)) {
                                           list.insert(insnNode.getPrevious(), new MethodInsnNode(Opcodes.INVOKESTATIC,
                                                   "me/towdium/jecharacters/utils/Match", "wrap",
                                                   "(Ljava/lang/String;)Ljava/lang/String;", false));
                                           return;
                                       }
                                   }
                               }
                           }
                   );

        return node;
    }

    @Override
    public boolean accept(String className) {
        return targets.contains(className);
    }

    @Override
    public Set<String> targetClasses() {
        return targets;
    }
}
