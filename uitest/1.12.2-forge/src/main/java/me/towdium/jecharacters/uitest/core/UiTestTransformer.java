package me.towdium.jecharacters.uitest.core;

import me.towdium.jecharacters.uitest.seam.Seams;
import net.minecraft.launchwrapper.IClassTransformer;

/**
 * The one transformer the harness's coremod registers. It retargets the input reads of any class
 * that polls the two LWJGL input classes, and hangs the recorder taps off the four JECh classes it
 * knows; everything else costs a byte scan and is left alone.
 *
 * <p>A class whose shape does not match what the seam expects (a different LWJGL build, a JECh
 * version without one of the taps) is logged and left untouched rather than half-rewritten: the
 * seam is all-or-nothing per class, and a harness that silently did nothing would be worse than one
 * that says so.
 */
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
