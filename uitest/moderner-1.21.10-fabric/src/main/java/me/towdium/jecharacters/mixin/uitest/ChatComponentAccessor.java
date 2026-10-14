package me.towdium.jecharacters.mixin.uitest;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * The message log behind the chat HUD, read-only.
 *
 * <p>A scenario that drives the real chat path — open the chat, type, submit — has to assert that the
 * command really ran, and the one place a client records that is the history it renders. Vanilla
 * exposes only {@code getRecentChat()}, which is what the player <em>sent</em>; received lines live
 * in {@code allMessages}, so the accessor is the read the assertion needs. Nothing is written.
 */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {

    @Accessor("allMessages")
    List<GuiMessage> jechtestAllMessages();
}
