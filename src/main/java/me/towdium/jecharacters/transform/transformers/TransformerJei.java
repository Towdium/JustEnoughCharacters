package me.towdium.jecharacters.transform.transformers;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.transform.Transformer;
import mezz.jei.config.Constants;
import net.minecraftforge.fml.common.Loader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Author: Towdium
 * Date:   13/06/17
 */

public class TransformerJei extends Transformer.Default {

    @Override
    public boolean accepts(String name) {
        boolean loading = name.equals("mezz.jei.ingredients.IngredientFilter");
        if (loading) {
            boolean isJei = Loader.instance().getModList()
                    .stream()
                    .anyMatch(mod -> mod.getModId().equals(Constants.MOD_ID) && mod.getName().equals(Constants.NAME));
            if (!isJei) return false;
        }
        return JechConfig.enableJEI && loading;
    }

    @Override
    public void transform(ClassNode n) {
        JechCore.LOG.info("Transforming class " + n.name + " for JEI integration.");
        Transformer.findMethod(n, "<init>").ifPresent(methodNode ->
                Transformer.transformConstruct(methodNode, "mezz/jei/suffixtree/GeneralizedSuffixTree",
                        "me/towdium/jecharacters/util/Match$FakeTree"));
        Transformer.findMethod(n, "createPrefixedSearchTree").ifPresent(methodNode ->
                Transformer.transformConstruct(methodNode, "mezz/jei/suffixtree/GeneralizedSuffixTree",
                        "me/towdium/jecharacters/util/Match$FakeTree"));
        if (JechConfig.enableForceQuote) Transformer.findMethod(n, "getElements").ifPresent(methodNode -> {
            InsnList list = methodNode.instructions;
            list.insert(list.get(3), new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "me/towdium/jecharacters/util/Match", "wrap",
                    "(Ljava/lang/String;)Ljava/lang/String;", false));
        });
    }

}
