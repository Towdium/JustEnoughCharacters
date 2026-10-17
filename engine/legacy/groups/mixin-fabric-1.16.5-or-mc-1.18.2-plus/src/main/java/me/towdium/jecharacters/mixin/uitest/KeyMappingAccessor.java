package me.towdium.jecharacters.mixin.uitest;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("ALL")
    static Map<String, KeyMapping> jechtestAll() {
        throw new AssertionError("mixin accessor was not applied");
    }
}
