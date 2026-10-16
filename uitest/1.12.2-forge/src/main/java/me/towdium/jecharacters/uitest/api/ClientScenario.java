package me.towdium.jecharacters.uitest.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ClientScenario {

    String name();

    String group() default "";

    String[] tags() default {};

    boolean requiresWorld() default true;

    int timeoutMs() default 0;

    int scenarioTimeoutMs() default 0;
}
