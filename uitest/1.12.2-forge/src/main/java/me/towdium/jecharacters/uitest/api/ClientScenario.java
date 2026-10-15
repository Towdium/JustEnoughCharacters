package me.towdium.jecharacters.uitest.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a scenario the harness can run.
 *
 * <p><b>Port note.</b> On 1.21.1 the same contract lives in the identically named annotation in the
 * NeoForge checkout, and discovery reads it from {@code ModList#getAllScanData}. 1.12.2 has no such
 * API; FML's {@code ASMDataTable} (handed to a mod in its {@code FMLPreInitializationEvent}) plays
 * that role and records the annotation from the raw class bytes, so the retention only has to be
 * {@code RUNTIME} for {@code getAnnotation} to read the loaded class the way the registry does.
 *
 * <p>The annotated class must be a public class with a public no-argument constructor and a
 * {@code public void define(ScenarioBuilder)} method.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ClientScenario {

    /** The unique name the selection grammar matches. */
    String name();

    /** A group label, joined into the selection's {@code tag:} terms; empty when unset. */
    String group() default "";

    /** Free-form tags the selection's {@code tag:} terms match. */
    String[] tags() default {};

    /** Whether the scenario needs a loaded world before it runs. */
    boolean requiresWorld() default true;

    /** The default per-step wait budget, in ms; {@code 0} means the run's default. */
    int timeoutMs() default 0;

    /** The whole-scenario budget, in ms; {@code 0} means the run's default. */
    int scenarioTimeoutMs() default 0;
}
