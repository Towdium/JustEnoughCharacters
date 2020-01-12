package me.towdium.jecharacters.core.transformers;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import mcp.MethodsReturnNonnullByDefault;
import me.towdium.jecharacters.core.Utilities;
import org.objectweb.asm.tree.MethodNode;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class TJeiTree implements ITransformer<MethodNode> {
    @Override
    public MethodNode transform(MethodNode input, ITransformerVotingContext context) {
        Utilities.transformConstruct(input, "mezz/jei/suffixtree/GeneralizedSuffixTree",
                "me/towdium/jecharacters/safe/JechSearcher");
        return input;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target> targets() {
        return new HashSet<>(Arrays.asList(
                Target.targetMethod(
                        "mezz.jei.ingredients.IngredientFilter",
                        "<init>",
                        "(Lmezz/jei/ingredients/IngredientBlacklistInternal;" +
                                "Lmezz/jei/config/IIngredientFilterConfig;Lmezz/jei/config/" +
                                "IEditModeConfig;Lmezz/jei/api/runtime/IIngredientManager;" +
                                "Lmezz/jei/api/helpers/IModIdHelper;)V"
                ),
                Target.targetMethod(
                        "mezz.jei.ingredients.IngredientFilter",
                        "createPrefixedSearchTree",
                        "(CLmezz/jei/ingredients/PrefixedSearchTree$IModeGetter;" +
                                "Lmezz/jei/ingredients/PrefixedSearchTree$IStringsGetter;)V"
                )
        ));
    }
}
