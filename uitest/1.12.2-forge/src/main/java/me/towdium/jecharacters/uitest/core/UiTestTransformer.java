package me.towdium.jecharacters.uitest.core;

import me.towdium.jecharacters.uitest.seam.Seams;
import net.minecraft.launchwrapper.IClassTransformer;

public class UiTestTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        String internalName = transformedName == null ? name : transformedName;
        internalName = internalName.replace('.', '/');
        try {
            Seams.Result result = Seams.transform(internalName, basicClass);
            if (result == null) return basicClass;
            UiTestCore.LOG.info("jechtest seam rewrote {}: {}", internalName, result.detail);
            return result.bytes;
        } catch (Throwable t) {
            UiTestCore.LOG.error("jechtest seam failed on {}; leaving it untouched", internalName, t);
            return basicClass;
        }
    }
}
