package me.towdium.jecharacters.uitest.api;

import org.jspecify.annotations.Nullable;

public record CheckResult(String label, boolean passed, @Nullable String detail) {

    public static CheckResult of(String label, boolean passed) {
        return new CheckResult(label, passed, null);
    }
}
