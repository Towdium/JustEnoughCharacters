package me.towdium.jecharacters.uitest;

import me.towdium.jecharacters.uitest.api.ClientScenario;
import me.towdium.jecharacters.uitest.api.ScenarioBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

@ClientScenario(
    name = "smoke",
    group = "smoke",
    tags = {"smoke"},
    timeoutMs = 30_000,
    scenarioTimeoutMs = 90_000
)
public final class SmokeScenario {

    private static final int walkTicks = 15;

    private static final double walkMinimum = 0.5;

    public void define(ScenarioBuilder build) {
        double[] before = new double[2];
        double[] after = new double[2];

        build.log("smoke: world ready, one full-frame capture, then the chat and walk input chain")
                .serverTicks(5)
                .awaitClientChunk(0, 0)
                .check("the client has a level", () -> Minecraft.getInstance().level != null)
                .check(
                    "the integrated server is running",
                    () -> Minecraft.getInstance().getSingleplayerServer() != null
                )
                .check("the local player exists", () -> Minecraft.getInstance().player != null)
                .screenshot("world")
                .checkCaptured("world")
                // region the screen-input chain
                .pressBind(options -> options.keyChat)
                .awaitScreen(ChatScreen.class)
                .checkScreen(ChatScreen.class)
                .type("jech smoke")
                .screenshot("chat")
                .checkCaptured("chat")
                .keyDown(GLFW.GLFW_KEY_ESCAPE)
                .waitUntil("the chat screen closes", () -> Minecraft.getInstance().screen == null)
                .check("no screen is open after escape", () -> Minecraft.getInstance().screen == null)
                // endregion
                // region the in-world hold, asserted on the player's own position
                .action("record the position before the walk", context -> {
                    Vector3d position = Minecraft.getInstance().player.position();
                    before[0] = position.x;
                    before[1] = position.z;
                })
                .holdBind(options -> options.keyUp)
                .ticks(walkTicks)
                .releaseBind(options -> options.keyUp)
                .action("record the position after the walk", context -> {
                    Vector3d position = Minecraft.getInstance().player.position();
                    after[0] = position.x;
                    after[1] = position.z;
                })
                .check(
                    "holding the forward bind displaced the player",
                    () -> horizontal(before, after) >= walkMinimum,
                    () -> "moved " + round(horizontal(before, after)) + " blocks, wanted at least " + walkMinimum
                )
                .action(
                    "log the walk",
                    context -> context.log(
                        "walk: " + round(before[0]) + "," + round(before[1]) + " -> " + round(after[0]) + ","
                                + round(after[1]) + " over " + walkTicks + " ticks"
                    )
                )
                // endregion
                .teardown(() -> Minecraft.getInstance().setScreen(null));
    }

    private static double horizontal(double[] from, double[] to) {
        return Math.hypot(to[0] - from[0], to[1] - from[1]);
    }

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
