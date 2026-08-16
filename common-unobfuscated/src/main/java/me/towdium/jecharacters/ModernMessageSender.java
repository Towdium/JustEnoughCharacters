package me.towdium.jecharacters;

import com.google.auto.service.AutoService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@AutoService(MessageSender.class)
public class ModernMessageSender implements MessageSender {
    private static final MethodHandle GET_CHAT = createGetChat();

    @Override
    public void sendMessage(String translationKey) {
        Minecraft minecraft = Minecraft.getInstance();

        minecraft.schedule(() -> {
            try {
                ChatComponent chat = (ChatComponent) GET_CHAT.invokeExact((Object) minecraft.gui);
                chat.addClientSystemMessage(Component.translatable(translationKey));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static MethodHandle createGetChat() {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();

        try {
            Class<?> guiClass = Minecraft.class.getField("gui").getType();

            // 26.1:
            // Gui#getChat() -> ChatComponent
            try {
                return lookup.findVirtual(guiClass, "getChat", MethodType.methodType(ChatComponent.class))
                             .asType(MethodType.methodType(ChatComponent.class, Object.class));
            } catch (NoSuchMethodException ignored) {}

            // 26.2:
            // Gui#hud -> Hud#getChat() -> ChatComponent
            Class<?> hudClass = guiClass.getField("hud").getType();

            MethodHandle getHud = lookup.findGetter(
                    guiClass,
                    "hud",
                    hudClass
            );

            MethodHandle getChat = lookup.findVirtual(
                    hudClass,
                    "getChat",
                    MethodType.methodType(ChatComponent.class)
            );

            return MethodHandles.filterReturnValue(getHud, getChat)
                    .asType(MethodType.methodType(
                            ChatComponent.class,
                            Object.class
                    ));

        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}