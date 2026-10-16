package me.towdium.jecharacters.mixin.uitest;

import net.minecraft.client.KeyboardListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardListener.class)
public interface KeyboardHandlerAccessor {

    @Invoker("charTyped")
    void invokeCharTyped(long window, int codePoint, int modifiers);
}
