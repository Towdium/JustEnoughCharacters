package me.towdium.jecharacters.uitest;

import me.towdium.jecharacters.uitest.api.ClientScenario;
import me.towdium.jecharacters.uitest.api.ScenarioBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;

/**
 * The bring-up scenario: the client booted, the harness created and pinned a world, a frame of that
 * world reaches the report, and the input chain really drives the game.
 *
 * <p>It is deliberately the smallest scenario that is worth anything: a failure in the world half says
 * "the run path is broken" and nothing else, and a failure in the input half says "the input seams are
 * broken" and nothing else.
 *
 * <p>The screenshot doubles as the flat-colour probe: a capture that comes back a single colour means
 * the readback read the wrong framebuffer.
 *
 * <h2>What the input half crosses</h2>
 *
 * <ol>
 *   <li><b>The in-world keyboard seam.</b> The chat box is opened by pressing the chat bind as a real
 *       key tap through the seam — a down and an up event that {@code Minecraft.runTickKeyboard}
 *       consumes and hands to {@code KeyBinding.onTick}, from which {@code Minecraft.processKeyBinds}
 *       opens the screen. Opening a screen is itself observable, so the next step asserts it arrived
 *       rather than assuming it did.</li>
 *   <li><b>The screen-space seam.</b> The chat line is typed one character event per character through
 *       the same seam, consumed by {@code GuiScreen.handleInput} and delivered to
 *       {@code GuiChat.keyTyped}.</li>
 *   <li><b>The held-key seam.</b> Escape is queued as a key-down event, which is what closes a chat
 *       box, and the close is then asserted. There is deliberately no matching key-up step: the screen
 *       a release would be dispatched at is gone by then, and the seam clears the hold when the run
 *       releases it.</li>
 * </ol>
 *
 * <p><b>Port note (what this scenario does not do).</b> The 1.21.1 smoke also walks the player forward
 * and asserts on its own position. That half is not here: on 1.21.1 walking is a scripted sweep through
 * the {@code PlayerEmbodiment} and the {@code InputDriver}'s in-world surface, and this line's
 * equivalent — a held movement bind across ticks — is the next wave's embodiment work.
 * {@link ScenarioBuilder#keyDown(int)} / {@link ScenarioBuilder#ticks(int)} /
 * {@link ScenarioBuilder#keyUp(int)} are the verbs it will be built from. The two halves that are here
 * are the ones the wave's acceptance names: the world reaches the report, and the screen input chain
 * really types.
 */
@ClientScenario(
        name = "smoke",
        group = "smoke",
        tags = {"smoke"},
        timeoutMs = 30_000,
        scenarioTimeoutMs = 90_000)
public final class SmokeScenario {

    /** The line typed into the chat box, one character event per character. */
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

    /**
     * What the open chat box holds, or a description of why there is nothing to read.
     *
     * <p>{@code GuiChat.inputField} is protected, and this is the 1.12.2 spelling of the 1.21.1
     * harness's {@code ChatComponentAccessor} mixin: the dev-only harness reaches the field by
     * reflection rather than by an accessor the shipped jar would carry. Both the development and the
     * reobfuscated spellings are tried, so the read does not depend on which mapping the client is
     * running under. A missing field is itself a failure the step reports, never an empty string that
     * would read as "nothing typed".
     */
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
                // try the other mapping's spelling
            } catch (IllegalAccessException e) {
                return "<chat input is not readable: " + e + ">";
            }
        }
        return "<GuiChat has no readable input field>";
    }
}
