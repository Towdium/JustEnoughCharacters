package me.towdium.jecharacters.mixin.uitest;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerAccessor {

    @Invoker("charTyped")
    void invokeCharTyped(long window, CharacterEvent event);

    @Invoker("keyPress")
    void invokeKeyPress(long window, int action, KeyEvent event);
}
