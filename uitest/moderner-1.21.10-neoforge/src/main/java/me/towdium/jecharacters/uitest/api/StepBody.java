package me.towdium.jecharacters.uitest.api;

/**
 * The body of one step. Called on the client thread, at most once per frame, with the re-entrancy
 * guard held.
 *
 * <p>A body that is not finished calls {@link TestContext#repeat(String)}: the runner then calls the
 * same body again next frame, without settling in between, until the step's wait budget runs out.
 */
@FunctionalInterface
public interface StepBody {

    /** Runs one attempt of the step. */
    void run(TestContext context);
}
