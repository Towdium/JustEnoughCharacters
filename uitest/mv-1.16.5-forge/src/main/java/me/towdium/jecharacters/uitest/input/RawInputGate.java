package me.towdium.jecharacters.uitest.input;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class RawInputGate {

    private static volatile @Nullable Object owner;

    private static volatile int syntheticDepth;

    private RawInputGate() {}

    public static void block(Object newOwner) {
        owner = newOwner;
    }

    public static void unblock(Object currentOwner) {
        if (owner == currentOwner) {
            owner = null;
        }
    }

    public static boolean isBlocked() {
        return owner != null;
    }

    public static boolean isBlockedBy(Object candidate) {
        return owner == candidate;
    }

    public static boolean blocksNow() {
        return owner != null && syntheticDepth == 0;
    }

    public static boolean isSynthetic() {
        return syntheticDepth > 0;
    }

    public static void runSynthetic(Runnable body) {
        syntheticDepth++;
        try {
            body.run();
        } finally {
            syntheticDepth--;
        }
    }

    public static <T> T callSynthetic(Supplier<T> body) {
        syntheticDepth++;
        try {
            return body.get();
        } finally {
            syntheticDepth--;
        }
    }
}
