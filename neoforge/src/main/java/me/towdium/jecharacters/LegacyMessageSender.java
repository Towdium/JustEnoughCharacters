package me.towdium.jecharacters;

import com.google.auto.service.AutoService;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

@AutoService(MessageSender.class)
public class LegacyMessageSender implements MessageSender {
    @Override
    public void sendMessage(String translationKey) {
        Minecraft.getInstance().schedule(() -> Minecraft.getInstance().gui.getChat().addMessage(Component.translatable(translationKey)));
    }
}
