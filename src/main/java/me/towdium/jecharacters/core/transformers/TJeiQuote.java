package me.towdium.jecharacters.core.transformers;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import mcp.MethodsReturnNonnullByDefault;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class TJeiQuote implements ITransformer<MethodNode> {
    @Override
    public MethodNode transform(MethodNode input, ITransformerVotingContext context) {
        InsnList list = input.instructions;
        list.insert(list.get(3), new MethodInsnNode(Opcodes.INVOKESTATIC,
                "me/towdium/jecharacters/JustEnoughCharacters", "wrap",
                "(Ljava/lang/String;)Ljava/lang/String;", false));
        return input;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target> targets() {
        return new HashSet<>(Collections.singletonList(
                Target.targetMethod(
                        "mezz.jei.ingredients.IngredientFilter",
                        "getElements",
                        "(Ljava/lang/String;)Lit/unimi/dsi/fastutil/ints/IntSet"
                )
        ));
    }
}
