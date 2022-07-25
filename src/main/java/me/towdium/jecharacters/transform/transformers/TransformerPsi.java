package me.towdium.jecharacters.transform.transformers;


import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.transform.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/**
 * Author: Towdium
 * Date: 29/04/19
 */
public class TransformerPsi extends Transformer.Default {

    @Override
    public boolean accepts(String name) {
        return JechConfig.enablePsi && name.equals("vazkii.psi.client.gui.GuiProgrammer");
    }

    @Override
    protected void transform(ClassNode n) {
        JechCore.LOG.info("Transforming class " + n.name + " for Psi integration.");
        Transformer.findMethod(n, "ranking").ifPresent(m ->
                Transformer.transformInvoke(m, "vazkii/psi/client/gui/GuiProgrammer", "rankTextToken",
                        "me/towdium/jecharacters/util/Match", "rank",
                        "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)I",
                        false, Opcodes.INVOKESTATIC, null, null
                )
        );
    }
}
