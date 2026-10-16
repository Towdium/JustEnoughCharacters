package me.towdium.jecharacters.mixin.uitest;

import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * The key mappings by name, which the game keeps private.
 *
 * <p>A scenario that has to press a bind the vanilla {@code GameSettings} does not carry — JEI's "select
 * search bar", say — names it the way the game does ({@code key.jei.focusSearch}) and has no other
 * way to reach the mapping: {@code KeyBinding.ALL} is private and holds every registered mapping,
 * vanilla and modded alike. Reading it is what lets the JSON step {@code press_bind} address any
 * bind by its own name instead of only the handful {@code GameSettings} exposes as fields.
 *
 * <p>The map is populated during mod construction and never mutated afterwards, so the read is
 * safe from the client thread a scenario runs on.
 */
@Mixin(KeyBinding.class)
public interface KeyMappingAccessor {

    @Accessor("ALL")
    static Map<String, KeyBinding> jechtestAll() {
        throw new AssertionError("mixin accessor was not applied");
    }
}
