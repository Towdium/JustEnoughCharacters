package me.towdium.jecharacters.uitest.input;

import java.util.concurrent.atomic.AtomicLong;

public final class PositionCorrections {

    private static final AtomicLong corrections = new AtomicLong();

    private PositionCorrections() {}

    public static void note() {
        corrections.incrementAndGet();
    }

    public static long count() {
        return corrections.get();
    }
}
