package me.towdium.jecharacters.uitest.input;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * The pointer override seam: a static "replace the source" hook the client mixins read.
 *
 * <p>Vanilla derives every pointer coordinate from {@code MouseHandler.xpos()/ypos()}: the
 * arguments of {@code Screen.mouseClicked/mouseReleased}, the mouse arguments
 * {@code Screen.render} receives, and CloudLib's own direct reads through
 * {@code ScreenUtil.getMousePos()} ({@code DragContextImpl}, {@code InputContext.fromEvent},
 * tooltips). Overriding those two getters therefore moves all four consumers at once.
 *
 * <p>Installing a {@link Source} is not a mixin toggle — the mixins are always applied; the
 * runtime override is what installs or removes the behaviour. A leaked source makes the real
 * pointer stop working, so the installer must clear it through
 * {@link #clearSource(Source)} (which only clears its own source).
 *
 * <p>Coordinates on both sides of this seam are <em>GUI-scaled</em>; the mixin converts to the
 * physical window space the getters are expected to return. The conversion deliberately reads
 * {@link Window} directly and never the overridden getter, which would recurse.
 */
public final class CursorState {

    /**
     * The override provider. Positions are GUI-scaled screen space — the space
     * {@code Screen.mouseClicked} takes and CloudLib's scene hit tests work in.
     */
    public interface Source {

        float cursorX();

        float cursorY();
    }

    private static volatile @Nullable Source source;

    private CursorState() {}

    /** Installs {@code newSource}, replacing any previous one. */
    public static void setSource(Source newSource) {
        source = newSource;
    }

    /**
     * Clears the source only when {@code owner} is the one currently installed, so overlapping
     * owners cannot clear each other.
     */
    public static void clearSource(Source owner) {
        if (source == owner) {
            source = null;
        }
    }

    public static @Nullable Source source() {
        return source;
    }

    public static boolean isInstalled() {
        return source != null;
    }

    /** The overridden cursor X in GUI-scaled space, or {@code 0} while nothing is installed. */
    public static float cursorX() {
        Source current = source;
        return current != null ? current.cursorX() : 0f;
    }

    /** The overridden cursor Y in GUI-scaled space, or {@code 0} while nothing is installed. */
    public static float cursorY() {
        Source current = source;
        return current != null ? current.cursorY() : 0f;
    }

    /** Converts a GUI-scaled X to the physical window X the mixin must hand back. */
    public static double toPhysicalX(float guiX) {
        Window window = window();
        return guiToPhysical(guiX, window.getScreenWidth(), window.getGuiScaledWidth());
    }

    /** Converts a GUI-scaled Y to the physical window Y the mixin must hand back. */
    public static double toPhysicalY(float guiY) {
        Window window = window();
        return guiToPhysical(guiY, window.getScreenHeight(), window.getGuiScaledHeight());
    }

    /** Converts a physical window X into GUI-scaled space. */
    public static float toGuiX(double physicalX) {
        Window window = window();
        return (float) physicalToGui(physicalX, window.getScreenWidth(), window.getGuiScaledWidth());
    }

    /** Converts a physical window Y into GUI-scaled space. */
    public static float toGuiY(double physicalY) {
        Window window = window();
        return (float) physicalToGui(physicalY, window.getScreenHeight(), window.getGuiScaledHeight());
    }

    /**
     * GUI to physical, the exact inverse of the scaling vanilla applies to the two getters —
     * {@code gui * screenExtent / guiScaledExtent}.
     */
    public static double guiToPhysical(double gui, int screenExtent, int guiScaledExtent) {
        return gui * screenExtent / Math.max(1, guiScaledExtent);
    }

    /** Physical to GUI — the inverse of {@link #guiToPhysical}. */
    public static double physicalToGui(double physical, int screenExtent, int guiScaledExtent) {
        return physical * Math.max(1, guiScaledExtent) / Math.max(1, screenExtent);
    }

    private static Window window() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getWindow();
    }
}
