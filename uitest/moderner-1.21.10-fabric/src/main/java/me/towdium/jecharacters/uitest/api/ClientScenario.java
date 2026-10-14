package me.towdium.jecharacters.uitest.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class that defines one scripted client scenario. Discovered by annotation scan across
 * every loaded mod, so a downstream mod ships scenarios without registering anything.
 *
 * <p>The annotated class must have a public no-argument constructor and a method that accepts a
 * {@link ScenarioBuilder}; the harness looks for {@code public void define(ScenarioBuilder)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ClientScenario {

    /** Unique name, used by the selection grammar and in the report. */
    String name();

    /** Free-form tags, matched by the {@code tag:} selection term. */
    String[] tags() default {};

    /** Whether the scenario needs a loaded world; a world-less scenario runs on the title screen. */
    boolean requiresWorld() default true;

    /** Optional grouping label, matched by the {@code group:} selection term. */
    String group() default "";

    /** GUI scale for this scenario, or {@code 0} to use the run's. */
    int guiScale() default 0;

    /**
     * Whether to hide the in-game HUD while this scenario runs.
     *
     * <p>Defaults to {@code false} because CloudLib's in-world layer is fully inert under
     * {@code hideGui}: panels park and nothing is captured.
     */
    boolean hideGui() default false;

    /** Settle time after each step in milliseconds, or {@code 0} to use the run's default. */
    int settleMs() default 0;

    /** Wait budget for a repeating step in milliseconds, or {@code 0} to use the run's default. */
    int timeoutMs() default 0;

    /** Budget for the whole scenario in milliseconds, or {@code 0} to use the run's default. */
    int scenarioTimeoutMs() default 0;

    /**
     * The least client session this scenario needs, which the runner's restart schedule is built
     * from: {@link Session#shared} (the default) needs nothing the run does not already have,
     * {@link Session#reenter} needs a fresh join in the same world, and {@link Session#fresh} needs a
     * world of its own.
     */
    Session session() default Session.shared;
}
