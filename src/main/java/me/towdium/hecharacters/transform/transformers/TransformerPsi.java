package me.towdium.hecharacters.transform.transformers;


import me.towdium.hecharacters.HechConfig;
import me.towdium.hecharacters.core.HechCore;
import me.towdium.hecharacters.match.PinyinMatcher;
import me.towdium.hecharacters.transform.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/**
 * Author: Towdium
 * Date: 29/04/19
 */
public class TransformerPsi extends Transformer.Default {
    public static int match(Object o, String a, String b) {
        return PinyinMatcher.contains(a, b) ? 1 : 0;
    }

    @Override
    public boolean accepts(String name) {
        return HechConfig.enablePsi && name.equals("vazkii.psi.client.gui.GuiProgrammer");
    }

    @Override
    protected void transform(ClassNode n) {
        HechCore.LOG.info("Transforming class " + n.name + " for Psi integration.");
        Transformer.findMethod(n, "ranking").ifPresent(m ->
                Transformer.transformInvoke(m, "vazkii/psi/client/gui/GuiProgrammer", "rankTextToken", "me/towdium/hecharacters/transform/transformers/TransformerPsi", "match",
                        "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)I",
                        false, Opcodes.INVOKESTATIC, null, null
                )
        );
    }
}
