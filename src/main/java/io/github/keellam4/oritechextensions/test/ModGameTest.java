package io.github.keellam4.oritechextensions.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a {@code public static void name(GameTestHelper)} method as a game test; {@link GameTests} registers it. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ModGameTest {
    /** Ticks before the test fails for not finishing. */
    int timeoutTicks() default 100;
}
