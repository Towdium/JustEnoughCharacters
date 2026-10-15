package me.towdium.jecharacters.mixin.uitest;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The one write into {@code KeyboardHandler}: {@code charTyped}, which is private.
 *
 * <p>{@code keyPress} is public and is called directly; {@code charTyped} is the only way a scripted
 * character reaches a screen's text field, so a scenario typing into the chat has to go through it
 * rather than through {@code Screen.charTyped} — that skips the screen's own NeoForge events, the
 * overlay guard and vanilla's code-point splitting, all of which a real keystroke passes through.
 *
 * <p>The invocation still runs the harness's own injected gates, which is why every synthetic call
 * is made inside {@link me.towdium.jecharacters.uitest.input.RawInputGate#runSynthetic}.
 */
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerAccessor {

    @Invoker("charTyped")
    void invokeCharTyped(long window, CharacterEvent event);

    /**
     * {@code keyPress}, which MC 1.21.10 made private.
     *
     * <p>It was public up to 1.21.9, so a scripted in-world key went straight through it; from
     * 1.21.10 the accessor is the only way in. The call still lands in the same body, so the
     * harness's own gate still sees it — which is why every synthetic call is made inside
     * {@link me.towdium.jecharacters.uitest.input.RawInputGate#runSynthetic}.
     */
    @Invoker("keyPress")
    void invokeKeyPress(long window, int action, KeyEvent event);
}
