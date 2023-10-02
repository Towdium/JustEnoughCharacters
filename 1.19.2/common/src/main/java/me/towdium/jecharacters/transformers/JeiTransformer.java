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
            "mezz.jei.gui.search.ElementPrefixParser",
            "mezz.jei.gui.ingredients.IngredientFilter"
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

        ITransformer.findMethod(node, "<init>", "(Lmezz/jei/api/runtime/IIngredientManager;Lmezz/jei/gui/config/IIngredientFilterConfig;Lmezz/jei/api/helpers/IColorHelper;)V")
                .ifPresent(method -> ITransformer.transformInvokeLambda(method,
                        "mezz/jei/core/search/suffixtree/GeneralizedSuffixTree",
                        "<init>",
                        "()V",
                        "me/towdium/jecharacters/utils/FakeTree",
                        "<init>",
                        "()V"
                ));
        ITransformer.findMethod(node, "parseSearchTokens", "(Ljava/lang/String;)Lmezz/jei/gui/ingredients/IngredientFilter$SearchTokens;")
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
                                    }
                                }
                            }
                        }
                );

        return node;
    }

    @Override
    public boolean accept(ClassNode node) {
        return targets.contains(node.name);
    }

    @Override
    public void init(JsonObject config, Set<String> removal) {

    }

    @Override
    public Set<String> targetClasses() {
        return targets;
    }
}
