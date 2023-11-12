package me.towdium.jecharacters.transformers;

import com.google.gson.JsonObject;
import me.towdium.jecharacters.asm.ITransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class JeiTransformer implements ITransformer {

    private static final Set<String> targets = new HashSet<>(Arrays.asList(
            "mezz/jei/common/search/ElementPrefixParser",
            "mezz/jei/common/ingredients/IngredientFilter"
    ));


    @Override
    public ClassNode transform(ClassNode node) {

        ITransformer.findMethod(node, "<clinit>", "()V")
                .ifPresent(method -> ITransformer.transformInvokeLambda(method,
                        "mezz/jei/core/search/suffixtree/GeneralizedSuffixTree",
                        "<init>",
                        "()V",
                        "me/towdium/jecharacters/utils/FakeTree",
                        "<init>",
                        "()V"
                ));

        ITransformer.findMethod(node, "<init>", "(Lmezz/jei/common/ingredients/RegisteredIngredients;Lmezz/jei/common/config/IIngredientFilterConfig;)V")
                .ifPresent(method -> ITransformer.transformInvokeLambda(method,
                        "mezz/jei/core/search/suffixtree/GeneralizedSuffixTree",
                        "<init>",
                        "()V",
                        "me/towdium/jecharacters/utils/FakeTree",
                        "<init>",
                        "()V"
                ));

        ITransformer.findMethod(node, "parseSearchTokens", "(Ljava/lang/String;)Lmezz/jei/common/ingredients/IngredientFilter$SearchTokens;")
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
    public void init(JsonObject config, Set<String> removal) {

    }

    @Override
    public Set<String> targetClasses() {
        return targets;
    }
}
