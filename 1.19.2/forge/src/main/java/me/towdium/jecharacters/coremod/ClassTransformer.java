package me.towdium.jecharacters.coremod;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import me.towdium.jecharacters.asm.JechClassTransformer;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;

import java.util.Set;
import java.util.stream.Collectors;

public class ClassTransformer implements ITransformer<ClassNode> {

    private final JechClassTransformer transformer;

    public ClassTransformer(JechClassTransformer transformer) {
        this.transformer = transformer;
    }

    @Override
    public String[] labels() {
        return new String[]{"JechClassTransformer"};
    }

    @Override
    public @NotNull ClassNode transform(@NotNull ClassNode input, @NotNull ITransformerVotingContext context) {
        transformer.transform(input);
        return input;
    }

    @Override
    public @NotNull TransformerVoteResult castVote(@NotNull ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public @NotNull Set<Target> targets() {
        return transformer.getTransformers()
                .stream()
                .flatMap(it -> it.targetClasses().stream())
                .map(Target::targetClass)
                .collect(Collectors.toSet());
    }
}
