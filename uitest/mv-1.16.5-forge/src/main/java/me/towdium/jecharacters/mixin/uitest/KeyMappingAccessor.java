package me.towdium.jecharacters.mixin.uitest;

import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(KeyBinding.class)
public interface KeyMappingAccessor {

    @Accessor("ALL")
    static Map<String, KeyBinding> jechtestAll() {
        throw new AssertionError("mixin accessor was not applied");
    }
}
