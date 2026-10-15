package me.towdium.jecharacters.uitest.api;

/**
 * What a step runs. Identical to the 1.21.1 interface of the same name; the {@code throws Exception}
 * is what lets a body call into the client's own checked-throwing entry points
 * ({@code GuiScreen.handleInput}, {@code Minecraft.runTick}) without wrapping every one.
 */
public interface StepBody {

    /** Runs one attempt of the step against the context the runner handed it. */
    void run(TestContext context) throws Exception;
}
