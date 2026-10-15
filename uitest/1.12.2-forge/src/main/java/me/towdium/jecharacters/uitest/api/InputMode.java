package me.towdium.jecharacters.uitest.api;

import java.util.Locale;

/**
 * How the run drives input.
 *
 * <p><b>Port note.</b> {@code real} is the 1.12.2 name for the mode that moves the OS cursor through
 * {@code java.awt.Robot}; 1.21.1 spells it {@code real} too. This wave implements only
 * {@link #synthetic} — the seam the coremod rewrites the LWJGL2 reads onto — and the real driver is
 * a later wave, so {@link #fromString} refuses to silently downgrade: naming {@code REAL} today is a
 * configuration error, not a quiet fallback.
 */
public enum InputMode {

    /** Input is answered by the harness's own seam; the OS pointer never moves. */
    synthetic,

    /** Input moves the real OS cursor; needs a visible, focused window. Not implemented yet. */
    real;

    /** Parses the {@code -PjechTestInputMode} value. */
    public static InputMode fromString(String value) {
        if (value == null) return synthetic;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "":
            case "synthetic":
                return synthetic;
            case "real":
                return real;
            default:
                throw new IllegalArgumentException("unknown input mode '" + value + "' (expected SYNTHETIC or REAL)");
        }
    }
}
