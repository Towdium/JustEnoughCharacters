package me.towdium.jecharacters.mixin.uitest;

import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

/**
 * Restores production semantics for {@link ForgeConfigSpec.ConfigValue#get()} in a dev run, on
 * the one Forge generation whose config loading still happens after mod construction.
 *
 * <p><b>Why this exists (Forge 43 / MC 1.19.2, measured 2026-10-15).</b> JECh 4.6.12's mod
 * constructor reads its config values, and on Forge 43 mod configs have not been loaded yet at
 * construction time — so in a dev run the very first read dies with
 * {@code IllegalStateException: Cannot get config value before config is loaded}. That throw is
 * deliberately dev-only (ForgeConfigSpec.java on branch 1.19.x wraps it in
 * {@code if (!FMLEnvironment.production)}): a production client falls through to
 * {@code defaultSupplier.get()} and the published mod keeps working, which is exactly how the
 * JECh jar under test behaves in every production launch of this line. This mixin takes the same
 * fall-through in the dev run, so the jar is exercised the way its users run it; once the config
 * loads, {@code isLoaded()} turns true and the vanilla path (including the value cache) runs
 * untouched.
 *
 * <p>Forge 47 (1.20.1) loads mod configs before construction, so that line needs no such bridge;
 * Forge 40 (1.18.2) has no dev-only throw at all — {@code get()} falls through to the default on
 * its own. The mixin belongs to {@code jechtest}, so a plain dev client never has it applied, and
 * the shipped jar carries neither this class nor the mixin config that names it.
 */
@Mixin(value = ForgeConfigSpec.ConfigValue.class, remap = false)
@SuppressWarnings({"unchecked", "rawtypes"}) // the target is generic; the seams are raw on purpose
public abstract class ConfigValueDefaultMixin {

    /** The spec this value belongs to, backfilled when the builder builds it (not final). */
    @Shadow
    private ForgeConfigSpec spec;

    @Shadow
    @Final
    private Supplier<?> defaultSupplier;

    @Inject(method = "get()Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
    private void jechtestDefaultBeforeLoad(CallbackInfoReturnable<Object> info) {
        if (!spec.isLoaded()) {
            info.setReturnValue(defaultSupplier.get());
        }
    }
}
