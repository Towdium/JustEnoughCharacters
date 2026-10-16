package me.towdium.jecharacters.mixin.uitest;

import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Mixin(value = ForgeConfigSpec.ConfigValue.class, remap = false)
@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class ConfigValueDefaultMixin {

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
