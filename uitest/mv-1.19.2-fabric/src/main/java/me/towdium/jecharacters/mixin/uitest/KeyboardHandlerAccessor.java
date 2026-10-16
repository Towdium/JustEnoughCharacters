package me.towdium.jecharacters.mixin.uitest;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerAccessor {

    @Invoker("charTyped")
    void invokeCharTyped(long window, int codePoint, int modifiers);
}
