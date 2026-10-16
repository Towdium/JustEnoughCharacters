package me.towdium.jecharacters.mixin.uitest;

import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.NewChatGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(NewChatGui.class)
public interface ChatComponentAccessor {

    @Accessor("allMessages")
    List<ChatLine> jechtestAllMessages();
}
