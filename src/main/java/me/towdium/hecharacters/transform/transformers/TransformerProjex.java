package me.towdium.hecharacters.transform.transformers;

import me.towdium.hecharacters.HechConfig;
import me.towdium.hecharacters.core.HechCore;
import me.towdium.hecharacters.match.PinyinMatcher;
import me.towdium.hecharacters.transform.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/**
 * Author: Towdium
 * Date:   13/09/20
 */
public class TransformerProjex extends Transformer.Default {
    public static int match(Object o, String a, String b) {
        return PinyinMatcher.contains(a, b) ? 1 : 0;
    }

    @Override
    public boolean accepts(String name) {
        return HechConfig.enableProjectEX && name.equals("com.latmod.mods.projectex.gui.GuiTableBase");
    }

    @Override
    protected void transform(ClassNode n) {
        HechCore.LOG.info("Transforming class " + n.name + " for Project EX integration.");
        Transformer.findMethod(n, "updateValidItemList").ifPresent(m ->
                Transformer.transformInvoke(m, "org/apache/commons/lang3/StringUtils", "contains", "me/towdium/hecharacters/transform/transformers/TransformerProjex", "contains",
                        "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z",
                        false, Opcodes.INVOKESTATIC, null, null
                )
        );
    }

    public static boolean contains(CharSequence a, CharSequence b) {
        return PinyinMatcher.contains(a.toString(), b);
    }
}
