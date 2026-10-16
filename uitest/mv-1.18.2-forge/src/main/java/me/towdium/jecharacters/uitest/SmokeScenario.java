package me.towdium.jecharacters.uitest;

import me.towdium.jecharacters.uitest.api.ClientScenario;
import me.towdium.jecharacters.uitest.api.ScenarioBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * The bring-up scenario: the client booted, the harness created and pinned a world, a frame of that
 * world reaches the report, and the input chain really drives the game.
 *
 * <p>It is deliberately the smallest scenario that is worth anything: a failure in the world half says
 * "the run path is broken" and nothing else, and a failure in the input half says "the input seams
 * are broken" and nothing else.
 *
 * <p>The screenshot doubles as the flat-colour probe: a capture that comes back a single colour means
 * the readback read the wrong framebuffer.
 *
 * <h2>What the input half crosses</h2>
 *
 * <ol>
 *   <li><b>The in-world keyboard seam.</b> The chat screen is opened by pressing the chat bind through
 *       {@code KeyboardHandler.keyPress} — the only writer of the {@code KeyMapping} a bind is read
 *       from — with the raw-input gate stepped aside for the synthetic call. Opening a screen is
 *       itself observable, so the next step asserts it arrived rather than assuming it did.</li>
 *   <li><b>The screen-space seam.</b> The chat line is typed one character per step through the
 *       driver's {@code charTyped}, dispatched at the live {@link ChatScreen}.</li>
 *   <li><b>The held-key seam.</b> Escape is pressed as a real key, which is what closes a chat box,
 *       and the close is then asserted. There is deliberately no matching release step: the screen a
 *       release would be dispatched at is gone by then, and the driver clears the hold on uninstall.</li>
 *   <li><b>The in-world hold.</b> Walking is a real key left down across ticks, so the assertion is
 *       the player's own position having moved — a real behaviour, not a marker.</li>
 * </ol>
 */
@ClientScenario(
    name = "smoke",
    group = "smoke",
    tags = {"smoke"},
    timeoutMs = 30_000,
    scenarioTimeoutMs = 90_000
)
public final class SmokeScenario {

    /** How long the forward bind is held, in client ticks. */
    private static final int walkTicks = 15;

    /** How far the player must have moved for the hold to have counted as a real walk, in blocks. */
    private static final double walkMinimum = 0.5;

    public void define(ScenarioBuilder build) {
        // Where the player stood before and after the walk. Captured here rather than in the context's
        // state because only this scenario reads it, and a step body is the only thing that may write
        // it: the reading is taken on the frame the step runs, which is the point.
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
                    Vec3 position = Minecraft.getInstance().player.position();
                    before[0] = position.x;
                    before[1] = position.z;
                })
                .holdBind(options -> options.keyUp)
                .ticks(walkTicks)
                .releaseBind(options -> options.keyUp)
                .action("record the position after the walk", context -> {
                    Vec3 position = Minecraft.getInstance().player.position();
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

    /** The player's horizontal displacement between two recorded positions. */
    private static double horizontal(double[] from, double[] to) {
        return Math.hypot(to[0] - from[0], to[1] - from[1]);
    }

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
