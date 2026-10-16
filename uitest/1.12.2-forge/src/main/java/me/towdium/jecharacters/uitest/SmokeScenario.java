package me.towdium.jecharacters.uitest;

import me.towdium.jecharacters.uitest.api.ClientScenario;
import me.towdium.jecharacters.uitest.api.ScenarioBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;

@ClientScenario(
        name = "smoke",
        group = "smoke",
        tags = {"smoke"},
        timeoutMs = 30_000,
        scenarioTimeoutMs = 90_000)
public final class SmokeScenario {

    private static final String typed = "jech smoke";

    public void define(ScenarioBuilder build) {
        build.log("smoke: world ready, one full-frame capture, then the chat input chain")
                .serverTicks(5)
                .check("the client has a world", new java.util.function.BooleanSupplier() {
                    @Override
                    public boolean getAsBoolean() {
                        return Minecraft.getMinecraft().world != null;
                    }
                })
                .check("the integrated server is running", new java.util.function.BooleanSupplier() {
                    @Override
                    public boolean getAsBoolean() {
                        return Minecraft.getMinecraft().getIntegratedServer() != null;
                    }
                })
                .check("the local player exists", new java.util.function.BooleanSupplier() {
                    @Override
                    public boolean getAsBoolean() {
                        return Minecraft.getMinecraft().player != null;
                    }
                })
                .screenshot("world")
                .checkCaptured("world")
                // region the screen input chain
                .openChat()
                .awaitScreen(GuiChat.class)
                .checkScreen(GuiChat.class)
                .type(typed)
                .action("log what the chat box holds", context -> context.log("chat input: \"" + chatInput() + "\""))
                .checkEquals("the chat box holds what was typed", typed, () -> chatInput())
                .screenshot("chat")
                .checkCaptured("chat")
                .keyDown(Keyboard.KEY_ESCAPE)
                .waitUntil("the chat screen closes", new java.util.function.BooleanSupplier() {
                    @Override
                    public boolean getAsBoolean() {
                        return Minecraft.getMinecraft().currentScreen == null;
                    }
                })
                .check("no screen is open after escape", new java.util.function.BooleanSupplier() {
                    @Override
                    public boolean getAsBoolean() {
                        return Minecraft.getMinecraft().currentScreen == null;
                    }
                })
                // endregion
                .teardown("close whatever screen is open", () -> Minecraft.getMinecraft().displayGuiScreen(null));
    }

    private static String chatInput() {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (!(screen instanceof GuiChat)) {
            return "<no chat screen: "
                    + (screen == null ? "no screen" : screen.getClass().getSimpleName()) + ">";
        }
        String[] names = {"inputField", "field_146415_a"};
        for (String name : names) {
            try {
                Field field = GuiChat.class.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(screen);
                if (value instanceof GuiTextField) {
                    return ((GuiTextField) value).getText();
                }
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException e) {
                return "<chat input is not readable: " + e + ">";
            }
        }
        return "<GuiChat has no readable input field>";
    }
}
