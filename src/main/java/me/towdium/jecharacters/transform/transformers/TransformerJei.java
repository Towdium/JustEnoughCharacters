package me.towdium.jecharacters.transform.transformers;

import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.transform.Transformer;
import mezz.jei.config.Constants;
import net.minecraftforge.fml.common.Loader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Iterator;

/**
 * Author: Towdium
 * Date:   13/06/17
 */

public class TransformerJei extends Transformer.Default {

    // True when the loaded mod is Had Enough Items, the JEI fork with the new search API.
    // Legacy JEI ("Just Enough Items") is handled with the classic ASM patches below.
    private boolean hei;

    @Override
    public boolean accepts(String name) {
        boolean loading = name.equals("mezz.jei.ingredients.IngredientFilter");
        if (loading) {
            boolean isJei = Loader.instance().getModList()
                    .stream()
                    .anyMatch(mod -> mod.getModId().equals(Constants.MOD_ID) &&
                            (mod.getName().equals("Just Enough Items") || mod.getName().equals("Had Enough Items")));
            if (!isJei) return false;
            hei = Loader.instance().getModList()
                    .stream()
                    .anyMatch(mod -> mod.getModId().equals(Constants.MOD_ID) && mod.getName().equals("Had Enough Items"));
        }
        return JechConfig.enableJEI && loading;
    }

    @Override
    public void transform(ClassNode n) {
        JechCore.LOG.info("Transforming class " + n.name + " for JEI integration.");
        if (hei) transformHei(n);
        else transformLegacyJei(n);
    }

    // Legacy JEI: replace its internal suffix tree with the pinyin tree.
    private void transformLegacyJei(ClassNode n) {
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

    // Had Enough Items: the search index is replaced through the plugin API (JechJeiPlugin),
    // only the force-quote hook needs to be patched in here, wrapping the filter text
    // before HEI tokenizes it.
    private void transformHei(ClassNode n) {
        if (!JechConfig.enableForceQuote) return;
        Transformer.findMethod(n, "getIngredientListUncached",
                "(Ljava/lang/String;)Ljava/util/List;").ifPresent(methodNode -> {
            Iterator<AbstractInsnNode> itr = methodNode.instructions.iterator();
            while (itr.hasNext()) {
                AbstractInsnNode insn = itr.next();
                if (insn instanceof MethodInsnNode && insn.getOpcode() == Opcodes.INVOKESTATIC) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (call.owner.equals("mezz/jei/search/SearchToken") &&
                            call.name.equals("parseSearchTokens")) {
                        methodNode.instructions.insertBefore(call, new MethodInsnNode(
                                Opcodes.INVOKESTATIC, "me/towdium/jecharacters/util/Match", "wrap",
                                "(Ljava/lang/String;)Ljava/lang/String;", false));
                        break;
                    }
                }
            }
        });
    }

}
