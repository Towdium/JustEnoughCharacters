package me.towdium.jecharacters.coremod;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import me.towdium.jecharacters.asm.JechClassTransformer;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class JechServiceProvider implements ITransformationService {

    @Override
    public @NotNull String name() {
        return "JechTransformService";
    }

    @Override
    public void initialize(@NotNull IEnvironment environment) {

    }

    @Override
    public void beginScanning(@NotNull IEnvironment environment) {

    }


    @Override
    public void onLoad(@NotNull IEnvironment env, @NotNull Set<String> otherServices) {

    }


    @SuppressWarnings("rawtypes")
    @Override
    public @NotNull List<ITransformer> transformers() {
        return Collections.singletonList(new ClassTransformer(JechClassTransformer.INSTANCE));
    }
}
